package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL
import ai.nixiesearch.util.source.SourceReader.SourceLocation
import cats.effect.IO
import fs2.Stream

trait SourceReader {
  def bytes(path: SourceLocation): Stream[IO, Byte]
}

object SourceReader {
  enum SourceLocation {
    case FileLocation(url: URL) extends SourceLocation
    case DirLocation(url: URL) extends SourceLocation
  }
}
