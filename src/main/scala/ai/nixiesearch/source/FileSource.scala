package ai.nixiesearch.source

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, JsonDocumentStream}
import ai.nixiesearch.main.CliConfig.IndexSourceArgs.FileIndexSourceArgs
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO

case class FileSource(config: FileIndexSourceArgs) extends DocumentSource {
  override def stream(mapping: IndexMapping): fs2.Stream[IO, Document] = URLReader
    .bytes(config.url, recursive = config.recursive)
    .through(JsonDocumentStream.parse(mapping))
    .chunkN(32)
    .unchunks
}
