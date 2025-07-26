package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.field.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.config.mapping.FieldName.StringName
import cats.effect.IO
import de.lhns.fs2.compress.{GzipCompressor, ZstdCompressor, ZstdDecompressor}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayOutputStream, FileInputStream}

class JsonDocumentStreamTest extends AnyFlatSpec with Matchers {
  val doc      = """{"_id":"1","text":"foo"}"""
  val expected = Document(List(TextField("_id", "1"), TextField("text", "foo")))
  val mapping  = IndexMapping(
    name = IndexName("test"),
    fields = Map(
      StringName("_id")  -> TextFieldSchema(name = StringName("_id")),
      StringName("text") -> TextFieldSchema(name = StringName("text"))
    )
  )

  it should "decode raw json" in {
    val result = Stream(doc.getBytes()*).through(JsonDocumentStream.parse(mapping)).compile.toList.unsafeRunSync()
    result shouldBe List(expected)
  }

  it should "decode gzipped json" in {
    val buffer = new ByteArrayOutputStream()
    val stream = new GzipCompressorOutputStream(buffer)
    stream.write(doc.getBytes())
    stream.close()
    val result = Stream(buffer.toByteArray*)
      .through(JsonDocumentStream.parse(mapping))
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe List(expected)
  }

  it should "decode zstd json" in {
    val zst    = ZstdCompressor.make[IO]()
    val result = Stream(doc.getBytes()*)
      .through(zst.compress)
      .through(JsonDocumentStream.parse(mapping))
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe List(expected)
  }

  it should "decode newline-delimited blobs" in {
    val input  = s"$doc\n$doc"
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
