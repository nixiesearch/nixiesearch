package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, DocumentDecoder, JsonDocumentStream}
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO
import fs2.io.readInputStream
import io.circe.parser.*
import fs2.Stream

import java.io.{File, FileInputStream}
import cats.effect.unsafe.implicits.global
import com.github.plokhotnyuk.jsoniter_scala.core.*
import io.circe.Decoder

object DatasetLoader {
  def fromFile(path: String, mapping: IndexMapping, limit: Int = 1000): List[Document] = {

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
