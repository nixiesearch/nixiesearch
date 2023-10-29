package ai.nixiesearch.api.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.TermAggSize.ExactTermAggSize
import ai.nixiesearch.core.FiniteRange.Higher.{Lt, Lte}
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import ai.nixiesearch.core.FiniteRange.{Higher, Lower}
import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

sealed trait Aggregation {
  def field: String
}

object Aggregation {
  sealed trait TermAggSize
  object TermAggSize {
    case class ExactTermAggSize(value: Int) extends TermAggSize
    case object AllTermAggSize              extends TermAggSize

    given termAggSizeEncoder: Encoder[TermAggSize] = Encoder.instance {
      case ExactTermAggSize(value) => Json.fromInt(value)
      case AllTermAggSize          => Json.fromString("all")
    }

    given termAggSizeDecoder: Decoder[TermAggSize] = Decoder.instance(c =>
      c.as[Int] match {
        case Left(err1) =>
          c.as[String] match {
            case Left(err2) =>
              Left(DecodingFailure(s"cannot decode term agg size: should be int|all, got ${c.value}", c.history))
            case Right("all") => Right(AllTermAggSize)
            case Right(other) =>
              Left(DecodingFailure(s"term agg size should be int|all, but got string '$other'", c.history))
          }
        case Right(int) => Right(ExactTermAggSize(int))
      }
    )
  }
  case class TermAggregation(field: String, size: TermAggSize = ExactTermAggSize(10)) extends Aggregation
  object TermAggregation {
    def apply(field: String, size: Int) = new TermAggregation(field, ExactTermAggSize(size))
  }
  case class RangeAggregation(field: String, ranges: List[AggRange]) extends Aggregation

  sealed trait AggRange {}

  object AggRange {

    case class RangeFrom(from: Lower)               extends AggRange
    case class RangeTo(to: Higher)                  extends AggRange
    case class RangeFromTo(from: Lower, to: Higher) extends AggRange

    given rangeEncoder: Encoder[AggRange] = Encoder.instance {
      case RangeFrom(from) => Json.obj(from.name -> Json.fromDoubleOrNull(from.value))
      case RangeTo(to)     => Json.obj(to.name -> Json.fromDoubleOrNull(to.value))
      case RangeFromTo(from, to) =>
        Json.obj(to.name -> Json.fromDoubleOrNull(to.value), from.name -> Json.fromDoubleOrNull(from.value))
    }

    given rangeDecoder: Decoder[AggRange] = Decoder.instance { c =>
      for {
        gt  <- c.downField("gt").as[Option[Double]]
        gte <- c.downField("gte").as[Option[Double]]
        lower <- (gt, gte) match {
          case (None, None)       => Right(None)
          case (Some(gt), None)   => Right(Some(Gt(gt)))
          case (None, Some(gte))  => Right(Some(Gte(gte)))
          case (Some(_), Some(_)) => Left(DecodingFailure(s"both gt+gte defined for range aggregation", c.history))
        }
        lt  <- c.downField("lt").as[Option[Double]]
        lte <- c.downField("lte").as[Option[Double]]
        higher <- (lt, lte) match {
          case (None, None)       => Right(None)
          case (Some(lt), None)   => Right(Some(Lt(lt)))
          case (None, Some(lte))  => Right(Some(Lte(lte)))
          case (Some(_), Some(_)) => Left(DecodingFailure(s"both lt+lte defined for range aggregation", c.history))
        }
        result <- (lower, higher) match {
          case (Some(f), Some(t)) => Right(RangeFromTo(f, t))
          case (None, Some(t))    => Right(RangeTo(t))
          case (Some(f), None)    => Right(RangeFrom(f))
          case (None, None)       => Left(DecodingFailure("range should have at least gt/gte/lt/lte field", c.history))
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
      size  <- c.downField("size").as[Option[TermAggSize]].map(_.getOrElse(ExactTermAggSize(10)))
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
