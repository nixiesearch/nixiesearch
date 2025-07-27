package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, JsonDocumentStream}
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO
import fs2.io.readInputStream
import io.circe.parser.*

import java.io.{File, FileInputStream}
import cats.effect.unsafe.implicits.global
import io.circe.Decoder

object DatasetLoader {
  def fromFile(path: String, mapping: IndexMapping, limit: Int = 1000): List[Document] = {
    given documentDecoder: Decoder[Document] = Document.decoderFor(mapping)

    readInputStream[IO](
      IO(new FileInputStream(new File(path))),
      1024000
    )
      .through(JsonDocumentStream.parse(mapping))
      .take(limit)
      .compile
      .toList
      .unsafeRunSync()

  }
}
