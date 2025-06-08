package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.rerank.RerankQuery
import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MatchQuery, MultiMatchQuery, RetrieveQuery}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.index.Models
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.search.IndexSearcher

trait Query extends Logging {
  def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SortPredicate],
      filter: Option[Filters],
      models: Models,
      aggs: Option[Aggs],
      size: Int
  ): IO[TopDocsWithFacets]
}

object Query extends Logging {

  given queryEncoder: Encoder[Query] = Encoder.instance {
    case q: RetrieveQuery => RetrieveQuery.retrieveQueryEncoder(q)
    case q: RerankQuery   => RerankQuery.rerankQueryEncoder(q)
  }

  given queryDecoder: Decoder[Query] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.keys.toList match {
          case head :: Nil if RerankQuery.supportedTypes.contains(head)   => RerankQuery.rerankQueryDecoder.tryDecode(c)
          case head :: Nil if RetrieveQuery.supportedTypes.contains(head) =>
            RetrieveQuery.retrieveQueryDecoder.tryDecode(c)
          case head :: Nil => Left(DecodingFailure(s"query type $head is not supported", c.history))
          case other => Left(DecodingFailure(s"query object should have exactly one key, but got $other", c.history))
        }
      case None => Left(DecodingFailure(s"query should be a json object, but got ${c.value}", c.history))
    }
  )

}
