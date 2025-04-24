package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.retrieve.MatchQuery.Operator
import ai.nixiesearch.api.query.retrieve.MatchQuery.Operator.OR
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.apache.lucene.search

case class MultiMatchQuery(query: String, fields: List[String], operator: Operator = OR) extends RetrieveQuery {
  override def compile(mapping: IndexMapping, filter: Option[Filters]): IO[search.Query] = ???
}

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
