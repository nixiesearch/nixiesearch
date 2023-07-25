package ai.nixiesearch.api.query

import ai.nixiesearch.api.query
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*

case class MultiMatchQuery(query: String, fields: List[String]) extends Query

object MultiMatchQuery {
  implicit val multiMatchQueryEncoder: Encoder[MultiMatchQuery] = deriveEncoder
  implicit val multiMatchQueryDecoder: Decoder[MultiMatchQuery] =
    deriveDecoder[query.MultiMatchQuery]
      .ensure(_.query.nonEmpty, "query cannot be empty")
      .ensure(_.fields.nonEmpty, "list of fields cannot be empty")
}
