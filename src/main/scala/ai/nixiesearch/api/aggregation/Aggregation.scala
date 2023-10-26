package ai.nixiesearch.api.aggregation

import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

sealed trait Aggregation {
  def field: String
}

object Aggregation {
  case class TermAggregation(field: String, size: Int = 10)          extends Aggregation
  case class RangeAggregation(field: String, ranges: List[AggRange]) extends Aggregation

  sealed trait AggRange {}

  object AggRange {
    
    
    case class RangeFrom(from: Double)               extends AggRange
    case class RangeTo(to: Double)                   extends AggRange
    case class RangeFromTo(from: Double, to: Double) extends AggRange

    given rangeEncoder: Encoder[AggRange] = Encoder.instance {
      case RangeFrom(from)       => Json.obj("from" -> Json.fromDoubleOrNull(from))
      case RangeTo(to)           => Json.obj("to" -> Json.fromDoubleOrNull(to))
      case RangeFromTo(from, to) => Json.obj("to" -> Json.fromDoubleOrNull(to), "from" -> Json.fromDoubleOrNull(from))
    }

    given rangeDecoder: Decoder[AggRange] = Decoder.instance { c =>
      for {
        from <- c.downField("from").as[Option[Double]]
        to   <- c.downField("to").as[Option[Double]]
        result <- (from, to) match {
          case (Some(f), Some(t)) => Right(RangeFromTo(f, t))
          case (None, Some(t))    => Right(RangeTo(t))
          case (Some(f), None)    => Right(RangeFrom(f))
          case (None, None)       => Left(DecodingFailure("range should have at least from or to field", c.history))
        }
      } yield {
        result
      }
    }
  }

  given termAggregationEncoder: Encoder[TermAggregation] = deriveEncoder
  given termAggregationDecoder: Decoder[TermAggregation] = Decoder.instance(c =>
    for {
      field <- c.downField("field").as[String]
      size  <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
    } yield {
      TermAggregation(field, size)
    }
  )

  given rangeAggregationEncoder: Encoder[RangeAggregation] = deriveEncoder
  given rangeAggregationDecoder: Decoder[RangeAggregation] = Decoder.instance(c =>
    for {
      field  <- c.downField("field").as[String]
      ranges <- c.downField("ranges").as[NonEmptyList[AggRange]]
    } yield {
      RangeAggregation(field, ranges.toList)
    }
  )

  given aggregationDecoder: Decoder[Aggregation] = Decoder.instance(c =>
    c.as[Map[String, Json]].map(_.toList) match {
      case Right((tpe, json) :: Nil) =>
        tpe match {
          case "term"  => termAggregationDecoder.decodeJson(json)
          case "range" => rangeAggregationDecoder.decodeJson(json)
          case other   => Left(DecodingFailure(s"aggregation $tpe is not supported", c.history))
        }
      case Right(head :: tail) => Left(DecodingFailure(s"aggregation should include only a single json key", c.history))
      case Right(Nil)          => Left(DecodingFailure(s"aggregation cannot be empty", c.history))
      case Left(err)           => Left(err)
    }
  )

  given aggregationEncoder: Encoder[Aggregation] = Encoder.instance {
    case a: TermAggregation  => Json.obj("term" -> termAggregationEncoder(a))
    case a: RangeAggregation => Json.obj("range" -> rangeAggregationEncoder(a))
  }
}
