package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MatchQuery, MultiMatchQuery}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.Searcher.TopDocsWithFacets
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.search.IndexSearcher

trait Query extends Logging {
  def topDocs(
      mapping: IndexMapping,
      searcher: IndexSearcher,
      sort: List[SortPredicate],
      filter: Option[Filters],
      size: Int
  ): IO[TopDocsWithFacets]
}

object Query extends Logging {

  implicit val queryEncoder: Encoder[Query] = Encoder.instance {
    case q: MatchAllQuery   => withType("match_all", MatchAllQuery.matchAllQueryEncoder(q))
    case q: MatchQuery      => withType("match", MatchQuery.matchQueryEncoder(q))
    case q: MultiMatchQuery => withType("multi_match", MultiMatchQuery.multiMatchQueryEncoder(q))
  }

  def withType(tpe: String, value: Json) = Json.fromJsonObject(JsonObject.fromIterable(List(tpe -> value)))

  implicit val queryDecoder: Decoder[Query] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toList.headOption match {
          case Some(("multi_match", json)) => MultiMatchQuery.multiMatchQueryDecoder.decodeJson(json)
          case Some(("match_all", json))   => MatchAllQuery.matchAllQueryDecoder.decodeJson(json)
          case Some(("match", json))       => MatchQuery.matchQueryDecoder.decodeJson(json)
          case Some((other, _))            => Left(DecodingFailure(s"query type '$other' not supported", c.history))
          case None => Left(DecodingFailure(s"expected non-empty json object, but got $obj", c.history))
        }
      case None => Left(DecodingFailure("expected json object", c.history))
    }
  )

}
