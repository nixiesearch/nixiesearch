package ai.nixiesearch.core

trait Field {
  def name: String
}

object Field {
  trait TextLikeField extends Field

  trait NumericField extends Field

  // case class DateField(name: String, value: Int) extends Field with NumericField
  // case class DateTimeField(name: String, value: Long) extends Field with NumericField
}
