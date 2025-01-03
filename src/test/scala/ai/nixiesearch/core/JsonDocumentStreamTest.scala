package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import cats.effect.unsafe.implicits.global

class JsonDocumentStreamTest extends AnyFlatSpec with Matchers {
  val doc      = """{"_id":"1","text":"foo"}"""
  val expected = Document(List(TextField("_id", "1"), TextField("text", "foo")))
  val mapping = IndexMapping(
    name = IndexName("test"),
    fields = Map(
      "_id"  -> TextFieldSchema(name = "_id"),
      "text" -> TextFieldSchema(name = "text")
    )
  )

  it should "decode raw json" in {
    val result = Stream(doc.getBytes()*).through(JsonDocumentStream.parse(mapping)).compile.toList.unsafeRunSync()
    result shouldBe List(expected)
  }

  it should "decode newline-delimited blobs" in {
    val input = s"$doc\n$doc"
    val result =
      Stream.emits(input.getBytes()).through(JsonDocumentStream.parse(mapping)).compile.toList.unsafeRunSync()
    result shouldBe List(expected, expected)
  }

  it should "decode json arrays" in {
    val input  = s"[$doc, $doc]"
    val result = Stream(input.getBytes()*).through(JsonDocumentStream.parse(mapping)).compile.toList.unsafeRunSync()
    result shouldBe List(expected, expected)
  }
}
