package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.field.TextListFieldCodec.NESTED_EMBED_SUFFIX
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.search.DocumentGroup.{PARENT_FIELD, ROLE_FIELD}
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.join.{DiversifyingChildrenFloatKnnVectorQuery, QueryBitSetProducer}
import org.apache.lucene.search.{BooleanClause, BooleanQuery, KnnFloatVectorQuery, Query, TermQuery}

case class KnnQuery(field: String, query_vector: Array[Float], k: Option[Int], num_candidates: Option[Int] = None)
    extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      maybeFilter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[Query] = {
    val realK   = k.getOrElse(10)
    val numCand = num_candidates.getOrElse(math.round(realK * 1.5).toInt)
    val finalK  = math.max(realK, numCand)
    for {
      luceneFilters <- maybeFilter match {
        case None          => IO(None)
        case Some(filters) => filters.toLuceneQuery(mapping)
      }
      schema <- IO.fromOption(mapping.fieldSchema[FieldSchema[?]](StringName(field)))(
        UserError(s"field '$field' not found in index mapping")
      )
      query <- schema match {
        case tf: TextFieldSchema if tf.search.semantic.nonEmpty =>
          IO.pure(new KnnFloatVectorQuery(field, query_vector, finalK, luceneFilters.orNull))
        case tlf: TextListFieldSchema if tlf.search.semantic.nonEmpty =>
          IO {
            val childFilter = new BooleanQuery.Builder()
            childFilter.add(new BooleanClause(new TermQuery(new Term(ROLE_FIELD, "child")), Occur.FILTER))
            childFilter.add(new BooleanClause(new TermQuery(new Term(PARENT_FIELD, field)), Occur.FILTER))
            val parentFilter = new BooleanQuery.Builder()
            parentFilter.add(new BooleanClause(new TermQuery(new Term(ROLE_FIELD, "parent")), Occur.FILTER))
            luceneFilters.foreach(q => parentFilter.add(new BooleanClause(q, Occur.FILTER)))

            new DiversifyingChildrenFloatKnnVectorQuery(
              field + NESTED_EMBED_SUFFIX,
              query_vector,
              childFilter.build(),
              finalK,
              new QueryBitSetProducer(parentFilter.build())
            )
          }

        case t: TextLikeFieldSchema[?] =>
          IO.raiseError(UserError(s"field '$field' is not semantically searchable, check the index mapping"))
        case other => IO.raiseError(UserError(s"field '$field' is not a text field"))
      }
    } yield {
      query
    }
  }
}

object KnnQuery {
  val MAX_NUM_CANDIDATES                   = 10000
  given knnQueryEncoder: Encoder[KnnQuery] = deriveEncoder
  given knnQueryDecoder: Decoder[KnnQuery] = Decoder.instance(c =>
    for {
      field          <- c.downField("field").as[String]
      queryVector    <- c.downField("query_vector").as[Array[Float]]
      k              <- c.downField("k").as[Option[Int]]
      num_candidates <- c.downField("num_candidates").as[Option[Int]].flatMap {
        case Some(value) if value > MAX_NUM_CANDIDATES =>
          Left(DecodingFailure(s"num_candidates should be less than $MAX_NUM_CANDIDATES", c.history))
        case Some(value) => Right(Some(value))
        case None        => Right(None)
      }
    } yield {
      KnnQuery(field, queryVector, k, num_candidates)
    }
  )
}
