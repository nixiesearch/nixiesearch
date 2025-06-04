package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.rerank.CEQuery.ContextFieldVisitor
import ai.nixiesearch.api.query.rerank.RerankQuery.ShardDoc
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import cats.effect.IO
import com.google.common.collect.Maps
import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.interpret.DynamicVariableResolver
import io.circe.{Decoder, Encoder}
import org.apache.lucene.search.TotalHits.Relation
import org.apache.lucene.search.{ScoreDoc, TopDocs, TotalHits}
import io.circe.generic.semiauto.*
import org.apache.lucene.index.StoredFieldVisitor.Status
import org.apache.lucene.index.{FieldInfo, StoredFieldVisitor}

import scala.collection.mutable

case class CEQuery(
    model: ModelRef,
    query: String,
    retrieve: Query,
    docTemplate: String,
    fields: Set[String],
    window: Option[Int] = None
) extends RerankQuery {
  lazy val jinja = new Jinjava()

  override def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SearchRoute.SortPredicate],
      filter: Option[Filters],
      models: Models,
      aggs: Option[Aggs],
      size: Int
  ): IO[Searcher.TopDocsWithFacets] = for {
    queryTopDocs <- retrieve.topDocs(mapping, readers, sort, filter, models, aggs, window.getOrElse(size))
    merged       <- combine(mapping, readers, models, queryTopDocs.docs, size)
  } yield {
    TopDocsWithFacets(merged, queryTopDocs.facets)
  }

  def combine(
      mapping: IndexMapping,
      readers: Readers,
      models: Models,
      docs: TopDocs,
      size: Int
  ): IO[TopDocs] = {
    for {
      texts <- IO {
        val docTexts = mutable.Map[ShardDoc, String]()
        for {
          scoreDoc <- docs.scoreDocs
        } {
          val doc = ShardDoc(scoreDoc.doc, scoreDoc.shardIndex)
          if (!docTexts.contains(doc)) {
            val visitor = ContextFieldVisitor(fields)
            readers.searcher.getIndexReader.storedFields().document(doc.docid, visitor)
            val text = jinja.render(docTemplate, visitor.context)
            docTexts.put(doc, text)
          }
        }
        docTexts.toList
      }
      scores <- models.ranker.score(model, query, texts.map(_._2))
    } yield {
      val topDocs = texts
        .map(_._1)
        .zip(scores)
        .sortBy(-_._2)
        .map { case (doc, score) =>
          new ScoreDoc(doc.docid, score, doc.shardIndex)
        }
        .take(size)
        .toArray
      new TopDocs(new TotalHits(topDocs.length, Relation.EQUAL_TO), topDocs)
    }
  }

}

object CEQuery {
  given ceQueryEncoder: Encoder[CEQuery] = deriveEncoder

  given ceQueryDecoder: Decoder[CEQuery] = Decoder.instance(c =>
    for {
      model    <- c.downField("model").as[ModelRef]
      query    <- c.downField("query").as[String]
      retrieve <- c.downField("retrieve").as[Query]
      template <- c.downField("doc_template").as[String]
      window   <- c.downField("rank_window_size").as[Option[Int]]
    } yield {
      CEQuery(
        model = model,
        query = query,
        retrieve = retrieve,
        docTemplate = template,
        window = window,
        fields = extractFields(template).toSet
      )
    }
  )

  case class ContextFieldVisitor(fields: Set[String], context: java.util.Map[String, String] = Maps.newHashMap())
      extends StoredFieldVisitor {
    override def needsField(fieldInfo: FieldInfo): StoredFieldVisitor.Status = {
      if (fields.contains(fieldInfo.name)) {
        Status.YES
      } else {
        Status.NO
      }
    }

    override def stringField(fieldInfo: FieldInfo, value: String): Unit = {
      context.put(fieldInfo.name, value)
    }

  }

  case class PeekingVariableResolver(variables: mutable.Buffer[String] = mutable.Buffer())
      extends DynamicVariableResolver {
    override def apply(t: String): AnyRef = {
      variables.append(t)
      null
    }
  }
  def extractFields(template: String): List[String] = {
    val jinja    = new Jinjava()
    val resolver = PeekingVariableResolver()
    jinja.getGlobalContext.setDynamicVariableResolver(resolver)
    jinja.render(template, Maps.newHashMap())
    resolver.variables.toList
  }
}
