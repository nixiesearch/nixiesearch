package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.{KnnFloatVectorQuery, Query}

case class KnnQuery(field: String, query_vector: Array[Float], k: Option[Int], num_candidates: Option[Int] = None)
    extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      maybeFilter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[Query] = for {
    schema <- IO.fromOption(mapping.fieldSchema(field))(UserError(s"field '$field' not found in index mapping"))
    _      <- schema match {
      case t: TextLikeFieldSchema[?] if t.search.semantic.nonEmpty => IO.unit
      case t: TextLikeFieldSchema[?]                               =>
        IO.raiseError(UserError(s"field '$field' is not lexically searchable, check the index mapping"))
      case other => IO.raiseError(UserError(s"field '$field' is not a text field"))
    }
    realK   = k.getOrElse(10)
    numCand = num_candidates.getOrElse(math.round(realK * 1.5).toInt)
    finalK  = math.max(realK, numCand)
    result <- maybeFilter match {
      case None          => IO(new KnnFloatVectorQuery(field, query_vector, finalK))
      case Some(filters) =>
        filters.toLuceneQuery(mapping).map {
          case Some(luceneFilters) => new KnnFloatVectorQuery(field, query_vector, finalK, luceneFilters)
          case None                => new KnnFloatVectorQuery(field, query_vector, finalK)
        }
    }
  } yield {
    result
  }
}

object KnnQuery {
  given knnQueryEncoder: Encoder[KnnQuery] = deriveEncoder
  given knnQueryDecoder: Decoder[KnnQuery] = Decoder.instance(c =>
    for {
      field          <- c.downField("field").as[String]
      queryVector    <- c.downField("query_vector").as[Array[Float]]
      k              <- c.downField("k").as[Option[Int]]
      num_candidates <- c.downField("num_candidates").as[Option[Int]]
    } yield {
      KnnQuery(field, queryVector, k, num_candidates)
    }
  )
}
