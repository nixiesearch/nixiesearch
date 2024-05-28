package ai.nixiesearch.source

import ai.nixiesearch.core.{Document, JsonDocumentStream}
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSourceArgs.FileIndexSourceArgs
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO

case class FileSource(config: FileIndexSourceArgs) extends DocumentSource {
  override def stream(): fs2.Stream[IO, Document] = URLReader
    .bytes(config.url, recursive = config.recursive)
    .through(JsonDocumentStream.parse)
    .chunkN(32)
    .unchunks
}
