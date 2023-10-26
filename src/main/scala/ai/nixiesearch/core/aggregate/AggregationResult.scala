package ai.nixiesearch.core.aggregate

import ai.nixiesearch.core.FiniteRange.{Higher, Lower}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

sealed trait AggregationResult

object AggregationResult {
  case class TermAggregationResult(buckets: List[TermCount]) extends AggregationResult
  case class TermCount(term: String, count: Int)

  given termCountEncoder: Encoder[TermCount]                         = deriveEncoder
  given termCountDecoder: Decoder[TermCount]                         = deriveDecoder
  given termAggregationResultEncoder: Encoder[TermAggregationResult] = deriveEncoder
  given termAggregationResultDecoder: Decoder[TermAggregationResult] = deriveDecoder

  given aggregationResultEncoder: Encoder[AggregationResult] = Encoder.instance {
    case t: TermAggregationResult  => termAggregationResultEncoder(t)
    case r: RangeAggregationResult => rangeAggregationResultEncoder(r)
  }

  given aggregationResultDecoder: Decoder[AggregationResult] = Decoder.instance(c => {
    termAggregationResultDecoder.tryDecode(c) match {
      case Left(value)  => rangeAggregationResultDecoder.tryDecode(c)
      case Right(value) => Right(value)
    }
  })

  case class RangeAggregationResult(buckets: List[RangeCount]) extends AggregationResult
  case class RangeCount(from: Option[Lower], to: Option[Higher], count: Int)

  given rangeCountEncoder: Encoder[RangeCount] = Encoder.instance { case RangeCount(from, to, count) =>
    Json.obj(
      List.concat(
        from.map(x => x.name -> Json.fromDoubleOrNull(x.value)).toList,
        to.map(x => x.name -> Json.fromDoubleOrNull(x.value)).toList,
        List("count" -> Json.fromInt(count))
      ): _*
    )
  }
  given rangeAggregationResultEncoder: Encoder[RangeAggregationResult] = deriveEncoder

  given rangeCountDecoder: Decoder[RangeCount]                         = deriveDecoder
  given rangeAggregationResultDecoder: Decoder[RangeAggregationResult] = deriveDecoder
}
