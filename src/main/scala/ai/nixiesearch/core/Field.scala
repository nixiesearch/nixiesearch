package ai.nixiesearch.core

import ai.nixiesearch.core.field.DateTimeFieldCodec

import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed trait Field {
  def name: String
}

object Field {
  sealed trait TextLikeField extends Field
  sealed trait NumericField  extends Field

  case class BooleanField(name: String, value: Boolean) extends Field {
    def intValue: Int = if (value) 1 else 0
  }

  case class DateField(name: String, value: Int) extends Field
  object DateField {
    def applyUnsafe(name: String, str: String): DateField = {
      val epoch = LocalDate.of(1970, 1, 1)
      val date  = LocalDate.parse(str)
      val days  = ChronoUnit.DAYS.between(epoch, date).toInt
      new DateField(name, days)
    }
  }

  case class DateTimeField(name: String, value: Long) extends Field
  object DateTimeField {
    def applyUnsafe(name: String, value: String): DateTimeField =
      DateTimeField(name, DateTimeFieldCodec.parseString(value).toOption.get)
  }

  case class DoubleField(name: String, value: Double) extends Field with NumericField

  case class DoubleListField(name: String, value: List[Double]) extends Field with NumericField

  case class FloatField(name: String, value: Float) extends Field with NumericField

  case class FloatListField(name: String, value: List[Float]) extends Field with NumericField

  case class GeopointField(name: String, lat: Double, lon: Double) extends Field

  case class IdField(name: String, value: String) extends Field

  case class IntField(name: String, value: Int) extends Field with NumericField

  case class IntListField(name: String, value: List[Int]) extends Field with NumericField

  case class LongField(name: String, value: Long) extends Field with NumericField

  case class LongListField(name: String, value: List[Long]) extends Field with NumericField

  case class TextField(name: String, value: String, embedding: Option[Array[Float]] = None)
      extends Field
      with TextLikeField
  object TextField {
    def apply(name: String, value: String) = {
      if (name == "_id") {
        throw new IllegalArgumentException("text field instead of id")
      } else {
        new TextField(name, value, None)
      }
    }
  }

  case class TextListField(name: String, value: List[String], embeddings: Option[List[Array[Float]]] = None)
      extends Field
      with TextLikeField

  object TextListField {
    def apply(name: String, value: String, values: String*) = new TextListField(name, value +: values.toList)
    def apply(name: String, values: List[String])           = new TextListField(name, values)
  }
}
