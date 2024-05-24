package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL
import cats.effect.IO
import fs2.Stream

trait SourceReader {
  def bytes(url: URL, recursive: Boolean = false): Stream[IO, Byte]
}

object SourceReader {}
