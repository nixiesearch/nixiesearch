package ai.nixiesearch.api.query

import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.{BooleanQuery, Query as LuceneQuery, MatchAllDocsQuery}

case class MatchAllQuery() extends Query {
  override def compile(mapping: IndexMapping): IO[LuceneQuery] = IO.pure(new MatchAllDocsQuery())
}

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}
