package ai.nixiesearch.api.query

import ai.nixiesearch.core.Logging
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
trait Query

object Query extends Logging {
  implicit val queryEncoder: Encoder[Query] = Encoder.instance {
    case q: MultiMatchQuery => encode("multi_match", q)
    case q: MatchAllQuery   => encode("match_all", q)
  }

  implicit val queryDecoder: Decoder[Query] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toList.headOption match {
          case Some(("multi_match", json)) => MultiMatchQuery.multiMatchQueryDecoder.decodeJson(json)
          case Some(("match_all", json))   => MatchAllQuery.matchAllQueryDecoder.decodeJson(json)
          case Some((other, _))            => Left(DecodingFailure(s"query type '$other' not supported", c.history))
          case None => Left(DecodingFailure(s"expected non-empty json object, but got $obj", c.history))
        }
      case None => Left(DecodingFailure("expected json object", c.history))
    }
  )

  def encode[T <: Query](name: String, q: T)(implicit encoder: Encoder[T]): Json =
    Json.fromJsonObject(JsonObject.fromIterable(List(name -> encoder(q))))
}
