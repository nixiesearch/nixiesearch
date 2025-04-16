package ai.nixiesearch.core

trait Field {
  def name: String
}

object Field {
  trait TextLikeField extends Field

  trait NumericField extends Field
}
