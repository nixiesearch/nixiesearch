package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MatchQuery.Operator.OR
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class MultiMatchQuery(query: String, fields: List[String], operator: Operator = OR) extends Query

object MultiMatchQuery {

  implicit val multiMatchQueryEncoder: Encoder[MultiMatchQuery] = deriveEncoder
  implicit val multiMatchQueryDecoder: Decoder[MultiMatchQuery] = Decoder
    .instance(c =>
      for {
        query  <- c.downField("query").as[String]
        fields <- c.downField("fields").as[List[String]]
        op     <- c.downField("operator").as[Option[Operator]].map(_.getOrElse(Operator.OR))
      } yield {
        MultiMatchQuery(query, fields, op)
      }
    )
    .ensure(_.query.nonEmpty, "query cannot be empty")
    .ensure(_.fields.nonEmpty, "list of fields cannot be empty")
}
