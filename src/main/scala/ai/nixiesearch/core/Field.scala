package ai.nixiesearch.core

trait Field {
  def name: String
}

object Field {
  trait TextLikeField                                         extends Field
  case class TextField(name: String, value: String)           extends Field with TextLikeField
  case class TextListField(name: String, value: List[String]) extends Field with TextLikeField
  object TextListField {
    def apply(name: String, value: String, values: String*) = new TextListField(name, value +: values.toList)
  }
  sealed trait NumericField                           extends Field
  case class IntField(name: String, value: Int)       extends Field with NumericField
  case class LongField(name: String, value: Long)     extends Field with NumericField
  case class FloatField(name: String, value: Float)   extends Field with NumericField
  case class DoubleField(name: String, value: Double) extends Field with NumericField
  case class BooleanField(name: String, value: Boolean) extends Field {
    def intValue: Int = if (value) 1 else 0
  }
  case class GeopointField(name: String, lat: Double, lon: Double) extends Field
  // case class DateField(name: String, value: Int) extends Field with NumericField
  // case class DateTimeField(name: String, value: Long) extends Field with NumericField
}
