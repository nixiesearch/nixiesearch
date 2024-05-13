package ai.nixiesearch.core

import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import cats.effect.unsafe.implicits.global

class JsonDocumentStreamTest extends AnyFlatSpec with Matchers {
  val doc      = """{"_id":"1","text":"foo"}"""
  val expected = Document(List(TextField("_id", "1"), TextField("text", "foo")))

  it should "decode raw json" in {
    val result = Stream(doc.getBytes()*).through(JsonDocumentStream.parse).compile.toList.unsafeRunSync()
    result shouldBe List(expected)
  }

  it should "decode newline-delimited blobs" in {
    val input  = s"$doc\n$doc"
    val result = Stream.emits(input.getBytes()).through(JsonDocumentStream.parse).compile.toList.unsafeRunSync()
    result shouldBe List(expected, expected)
  }

  it should "decode json arrays" in {
    val input  = s"[$doc, $doc]"
    val result = Stream(input.getBytes()*).through(JsonDocumentStream.parse).compile.toList.unsafeRunSync()
    result shouldBe List(expected, expected)
  }
}
