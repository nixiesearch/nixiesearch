package ai.nixiesearch.core.aggregate

import ai.nixiesearch.core.FiniteRange.{Higher, Lower, RangeValue}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
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
        from.map(x => x.name -> x.value.json).toList,
        to.map(x => x.name -> x.value.json).toList,
        List("count" -> Json.fromInt(count))
      )*
    )
  }
  given rangeAggregationResultEncoder: Encoder[RangeAggregationResult] = deriveEncoder

  given rangeCountDecoder: Decoder[RangeCount] = Decoder.instance(c =>
    for {
      gtOption  <- c.downField("gt").as[Option[RangeValue]]
      gteOption <- c.downField("gte").as[Option[RangeValue]]
      from <- (gtOption, gteOption) match {
        case (Some(gt), None)  => Right(Some(Lower.Gt(gt)))
        case (None, Some(gte)) => Right(Some(Lower.Gte(gte)))
        case (None, None)      => Right(None)
        case (Some(gt), Some(gte)) =>
          Left(DecodingFailure(s"both gt and gte options present, should be only one: ${c.focus}", c.history))
      }
      ltOption  <- c.downField("lt").as[Option[RangeValue]]
      lteOption <- c.downField("lte").as[Option[RangeValue]]
      to <- (ltOption, lteOption) match {
        case (Some(lt), None)  => Right(Some(Higher.Lt(lt)))
        case (None, Some(lte)) => Right(Some(Higher.Lte(lte)))
        case (None, None)      => Right(None)
        case (Some(lt), Some(lte)) =>
          Left(DecodingFailure(s"both lt and lte options present, should be only one: ${c.focus}", c.history))
      }
      count <- c.downField("count").as[Int]
    } yield {
      RangeCount(from, to, count)
    }
  )
  given rangeAggregationResultDecoder: Decoder[RangeAggregationResult] = deriveDecoder
}
