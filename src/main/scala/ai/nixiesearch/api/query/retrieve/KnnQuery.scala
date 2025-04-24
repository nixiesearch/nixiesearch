package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.{KnnFloatVectorQuery, Query}

case class KnnQuery(field: String, query_vector: Array[Float], k: Int = 10, num_candidates: Int = 10)
    extends RetrieveQuery {
  override def compile(mapping: IndexMapping, maybeFilter: Option[Filters], encoders: EmbedModelDict): IO[Query] = maybeFilter match {
    case None => IO(new KnnFloatVectorQuery(field, query_vector, k))
    case Some(filters) =>
      filters.toLuceneQuery(mapping).map {
        case Some(luceneFilters) => new KnnFloatVectorQuery(field, query_vector, k, luceneFilters)
        case None                => new KnnFloatVectorQuery(field, query_vector, k)
      }
  }
}

object KnnQuery {
  given knnQueryEncoder: Encoder[KnnQuery] = deriveEncoder
  given knnQueryDecoder: Decoder[KnnQuery] = Decoder.instance(c =>
    for {
      field         <- c.downField("field").as[String]
      queryVector   <- c.downField("query_vector").as[Array[Float]]
      k             <- c.downField("k").as[Option[Int]]
      numCandidates <- c.downField("num_candidates").as[Option[Int]]
    } yield {
      KnnQuery(field, queryVector, k.getOrElse(10), numCandidates.getOrElse(10))
    }
  )
}
