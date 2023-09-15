package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexReader
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.search.{
  BooleanQuery,
  IndexSearcher,
  MatchAllDocsQuery,
  MultiCollector,
  TopScoreDocCollector,
  Query as LuceneQuery
}

case class MatchAllQuery() extends Query

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}
