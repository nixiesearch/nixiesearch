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
  case class RangeValue(value: BigDecimal, json: Json) {
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
            case Some(bd) => Right(RangeValue(bd, c.value))
            case None     => Left(DecodingFailure(s"cannot decode ${num} as a range value", c.history))
          },
        jsonString = {
          case DateTimeTerm(millis) => Right(RangeValue(BigDecimal(millis), c.value))
          case DateTerm(days)       => Right(RangeValue(BigDecimal(days), c.value))
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
    object Gt {
      def apply(int: Int)      = new Gt(RangeValue(BigDecimal(int), Json.fromInt(int)))
      def apply(long: Long)    = new Gt(RangeValue(BigDecimal(long), Json.fromLong(long)))
      def apply(float: Double) = new Gt(RangeValue(BigDecimal(float), Json.fromDoubleOrNull(float)))
    }

    case class Gte(value: RangeValue) extends Lower {
      val name      = "gte"
      val inclusive = true
    }
    object Gte {
      def apply(int: Int)      = new Gte(RangeValue(BigDecimal(int), Json.fromInt(int)))
      def apply(long: Long)    = new Gte(RangeValue(BigDecimal(long), Json.fromLong(long)))
      def apply(float: Double) = new Gte(RangeValue(BigDecimal(float), Json.fromDoubleOrNull(float)))
    }
  }

  sealed trait Higher extends FiniteRange

  object Higher {
    case class Lt(value: RangeValue) extends Higher {
      val name      = "lt"
      val inclusive = false
    }
    object Lt {
      def apply(int: Int)      = new Lt(RangeValue(BigDecimal(int), Json.fromInt(int)))
      def apply(long: Long)    = new Lt(RangeValue(BigDecimal(long), Json.fromLong(long)))
      def apply(float: Double) = new Lt(RangeValue(BigDecimal(float), Json.fromDoubleOrNull(float)))
    }

    case class Lte(value: RangeValue) extends Higher {
      val name      = "lte"
      val inclusive = true
    }
    object Lte {
      def apply(int: Int)      = new Lte(RangeValue(BigDecimal(int), Json.fromInt(int)))
      def apply(long: Long)    = new Lte(RangeValue(BigDecimal(long), Json.fromLong(long)))
      def apply(float: Double) = new Lte(RangeValue(BigDecimal(float), Json.fromDoubleOrNull(float)))
    }
  }
}
