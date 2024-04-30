package ai.nixiesearch.index.store

import ai.nixiesearch.config.StoreConfig.StoreUrl.S3StoreUrl
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.lucene.store.{Directory, FilterDirectory, IOContext}
import io.circe.parser.*
import io.circe.syntax.*

import java.nio.file.Path

case class S3AsyncDirectory(inner: Directory) extends AsyncDirectory(inner) with Logging {}

object S3AsyncDirectory {
  def init(url: S3StoreUrl, path: Path): IO[S3AsyncDirectory] = ???
}
