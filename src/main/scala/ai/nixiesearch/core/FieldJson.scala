package ai.nixiesearch.core

import io.circe.Json

trait FieldJson[T <: Field] {
  def encode(field: T): Json
  def decode(): Int
}
