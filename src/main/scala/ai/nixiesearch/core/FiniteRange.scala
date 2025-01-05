package ai.nixiesearch.core

import ai.nixiesearch.api.filter.Predicate.FilterTerm.{DateTerm, DateTimeTerm}
import ai.nixiesearch.core.FiniteRange.RangeValue
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

sealed trait FiniteRange {
  def value: RangeValue
  def name: String
  def inclusive: Boolean
}

object FiniteRange {
  // sealed trait RangeValue
  // type RangeValue = BigDecimal
  case class RangeValue(value: BigDecimal) extends AnyVal {
    def toInt    = value.toInt
    def toLong   = value.toLong
    def toFloat  = value.toFloat
    def toDouble = value.toDouble
  }
  object RangeValue {

    given rangeValueEncoder: Encoder[RangeValue] = Encoder.instance { value => Json.fromBigDecimal(value.value) }

    given rangeValueDecoder: Decoder[RangeValue] = Decoder.instance(c =>
      c.value.fold(
        jsonNull = Left(DecodingFailure(s"range cannot be null: got ${c.value}", c.history)),
        jsonBoolean = bool => Left(DecodingFailure(s"range cannot be boolean: got ${c.value}", c.history)),
        jsonNumber = num =>
          num.toBigDecimal match {
            case Some(bd) => Right(RangeValue(bd))
            case None     => Left(DecodingFailure(s"cannot decode ${num} as a range value", c.history))
          },
        jsonString = {
          case DateTerm(days)       => Right(RangeValue(BigDecimal(days)))
          case DateTimeTerm(millis) => Right(RangeValue(BigDecimal(millis)))
          case _ => Left(DecodingFailure(s"range should be ISO date/datetime: got ${c.value}", c.history))
        },
        jsonObject = _ => Left(DecodingFailure(s"range cannot be object: got ${c.value}", c.history)),
        jsonArray = _ => Left(DecodingFailure(s"range cannot be array: got ${c.value}", c.history))
      )
    )
  }

  sealed trait Lower extends FiniteRange
  object Lower {
    case class Gt(value: RangeValue) extends Lower {
      val name      = "gt"
      val inclusive = false
    }

    case class Gte(value: RangeValue) extends Lower {
      val name      = "gte"
      val inclusive = true
    }
  }

  sealed trait Higher extends FiniteRange

  object Higher {
    case class Lt(value: RangeValue) extends Higher {
      val name      = "lt"
      val inclusive = false
    }

    case class Lte(value: RangeValue) extends Higher {
      val name      = "lte"
      val inclusive = true
    }
  }
}
