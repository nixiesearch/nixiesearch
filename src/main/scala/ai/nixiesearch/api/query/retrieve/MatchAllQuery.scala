package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.apache.lucene.search
import org.apache.lucene.search.MatchAllDocsQuery

case class MatchAllQuery() extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      filter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[search.Query] =
    applyFilters(mapping, new MatchAllDocsQuery(), filter)
}

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}
