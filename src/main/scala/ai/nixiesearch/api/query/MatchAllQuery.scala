package ai.nixiesearch.api.query

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class MatchAllQuery() extends Query

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}
