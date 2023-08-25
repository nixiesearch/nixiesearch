package ai.nixiesearch.api.aggregation

import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax.*

case class Aggs(aggs: Map[String, Aggregation] = Map.empty)

object Aggs {
  given aggsDecoder: Decoder[Aggs] = Decoder.instance(c => c.as[Map[String, Aggregation]].map(map => Aggs(map)))
  given aggsEncoder: Encoder[Aggs] = Encoder.instance(aggs =>
    Json.fromJsonObject(JsonObject.fromMap(aggs.aggs.map { case (name, agg) =>
      name -> Aggregation.aggregationEncoder(agg)
    }))
  )
}
