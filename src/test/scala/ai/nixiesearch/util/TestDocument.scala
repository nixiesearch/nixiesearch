package ai.nixiesearch.util

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IntField, TextField}

import scala.util.Random

object TestDocument {
  def apply() = new Document(
    List(
      TextField("_id", math.abs(Random.nextInt()).toString),
      TextField("title", "hello"),
      IntField("price", Random.nextInt(100))
    )
  )
}
