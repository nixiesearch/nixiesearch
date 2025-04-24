package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.apache.lucene.search
import org.apache.lucene.search.MatchAllDocsQuery

case class MatchAllQuery() extends RetrieveQuery {
  override def compile(mapping: IndexMapping, filter: Option[Filters]): IO[search.Query] =
    filter match {
      case Some(value) =>
        value.toLuceneQuery(mapping).flatMap {
          case Some(filterQuery) => IO.pure(filterQuery)
          case None              => IO.pure(new MatchAllDocsQuery())
        }
      case None => IO.pure(new MatchAllDocsQuery())
    }
}

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}
