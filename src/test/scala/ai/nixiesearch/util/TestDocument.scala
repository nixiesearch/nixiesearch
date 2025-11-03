package ai.nixiesearch.util

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, IntField, TextField}

import scala.util.Random

object TestDocument {
  def apply(
      id: String = math.abs(Random.nextInt()).toString,
      title: String = "hello",
      price: Int = Random.nextInt(100)
  ) = new Document(
    List(
      IdField("_id", id),
      TextField("title", "hello"),
      IntField("price", price)
    )
  )
}
