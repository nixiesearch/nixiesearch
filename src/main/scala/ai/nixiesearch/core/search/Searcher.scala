package ai.nixiesearch.core.search

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, MatchQuery, MultiMatchQuery}
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, SemanticSearch}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.aggregate.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.core.search.lucene.{LexicalLuceneQuery, MatchAllLuceneQuery, SemanticLuceneQuery}
import ai.nixiesearch.index.IndexReader
import cats.data.NonEmptyList
import cats.effect.IO
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  IndexSearcher,
  KnnFloatVectorQuery,
  MultiCollector,
  ScoreDoc,
  TermQuery,
  TopDocs,
  TopScoreDocCollector,
  TotalHits,
  Query as LuceneQuery
}
import cats.implicits.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.TotalHits.Relation

import scala.collection.mutable

object Searcher {
  case class FieldTopDocs(docs: TopDocs, facets: FacetsCollector)

  def search(request: SearchRequest, reader: IndexReader): IO[SearchResponse] = for {
    start   <- IO(System.currentTimeMillis())
    mapping <- reader.mapping()
    queries <- request.query match {
      case MatchAllQuery() => MatchAllLuceneQuery.create(request.filter, mapping)
      case MatchQuery(field, query, operator) =>
        fieldQuery(mapping, request.filter, field, query, operator.occur, request.size, reader.encoders)
      case MultiMatchQuery(query, fields, operator) =>
        fields
          .traverse(field =>
            fieldQuery(mapping, request.filter, field, query, operator.occur, request.size, reader.encoders)
          )
          .map(_.flatten)
    }
    fieldTopDocs  <- queries.traverse(query => searchField(reader.searcher, query, request.size))
    mergedFacets  <- IO(MergedFacetCollector(fieldTopDocs.map(_.facets)))
    mergedTopDocs <- reciprocalRank(fieldTopDocs.map(_.docs))
    aggs          <- aggregate(mapping, reader, mergedFacets, request.aggs)
    collected     <- collect(mapping, reader, mergedTopDocs, request.fields)
    end           <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(
      took = end - start,
      hits = collected,
      aggs = aggs
    )
  }

  def fieldQuery(
      mapping: IndexMapping,
      filter: Filters,
      field: String,
      query: String,
      operator: Occur,
      size: Int,
      encoders: BiEncoderCache
  ): IO[List[LuceneQuery]] =
    mapping.fields.get(field) match {
      case None => IO.raiseError(new Exception(s"Cannot search over undefined field $field"))
      case Some(TextLikeFieldSchema(_, LexicalSearch(language), _, _, _, _)) =>
        LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
      case Some(TextLikeFieldSchema(_, SemanticSearch(model, prefix), _, _, _, _)) =>
        SemanticLuceneQuery
          .create(
            encoders = encoders,
            model = model,
            prefix = prefix,
            query = query,
            field = field,
            size = size,
            filter = filter,
            mapping = mapping
          )
      case Some(TextLikeFieldSchema(_, HybridSearch(model, prefix, language), _, _, _, _)) =>
        for {
          x1 <- LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
          x2 <- SemanticLuceneQuery
            .create(
              encoders = encoders,
              model = model,
              prefix = prefix,
              query = query,
              field = field,
              size = size,
              filter = filter,
              mapping = mapping
            )

        } yield {
          x1 ++ x2
        }

      case Some(other) => IO.raiseError(new Exception(s"Cannot search over non-text field $field"))
    }

  def searchField(searcher: IndexSearcher, query: LuceneQuery, size: Int): IO[FieldTopDocs] = for {
    topCollector   <- IO.pure(TopScoreDocCollector.create(size, size))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(searcher.search(query, collector))
  } yield {
    FieldTopDocs(docs = topCollector.topDocs(), facets = facetCollector)
  }

  case class ShardDoc(docid: Int, shardIndex: Int)
  val K = 60.0f
  def reciprocalRank(topDocs: List[TopDocs]): IO[TopDocs] = topDocs match {
    case head :: Nil => IO.pure(head)
    case Nil         => IO.raiseError(new Exception(s"cannot merge zero query results"))
    case list =>
      IO {
        val docScores = mutable.Map[ShardDoc, Float]()
        for {
          ranking           <- list
          (scoreDoc, index) <- ranking.scoreDocs.zipWithIndex
        } {
          val doc   = ShardDoc(scoreDoc.doc, scoreDoc.shardIndex)
          val score = docScores.getOrElse(doc, 0.0f)
          docScores.put(doc, (score + 1.0f / (K + index)))
        }
        val topDocs = docScores.toList
          .sortBy(-_._2)
          .map { case (doc, score) =>
            new ScoreDoc(doc.docid, score, doc.shardIndex)
          }
          .toArray
        new TopDocs(new TotalHits(topDocs.length, Relation.EQUAL_TO), topDocs)
      }
  }

  def aggregate(
      mapping: IndexMapping,
      reader: IndexReader,
      collector: FacetsCollector,
      aggs: Aggs
  ): IO[Map[String, AggregationResult]] = aggs.aggs.toList
    .traverse { case (name, agg) =>
      mapping.fields.get(agg.field) match {
        case Some(field) if !field.facet =>
          IO.raiseError(new Exception(s"cannot aggregate over a field marked as a non-facetable"))
        case None => IO.raiseError(new Exception(s"cannot aggregate over a field not defined in schema"))
        case Some(schema) =>
          agg match {
            case a @ Aggregation.TermAggregation(field, size) =>
              TermAggregator.aggregate(reader.reader, a, collector, schema).map(result => name -> result)
            case a @ Aggregation.RangeAggregation(field, ranges) =>
              RangeAggregator.aggregate(reader.reader, a, collector, schema).map(result => name -> result)
          }
      }
    }
    .map(_.toMap)

  protected def collect(
      mapping: IndexMapping,
      reader: IndexReader,
      top: TopDocs,
      fields: NonEmptyList[String]
  ): IO[List[Document]] = IO {
    val fieldSet = fields.toList.toSet
    val docs = top.scoreDocs.map(doc => {
      val visitor = DocumentVisitor(mapping, fieldSet)
      reader.reader.storedFields().document(doc.doc, visitor)
      visitor.asDocument(doc.score)
    })
    docs.toList
  }

}
