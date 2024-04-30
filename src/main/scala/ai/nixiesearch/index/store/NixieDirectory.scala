package ai.nixiesearch.index.store

import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.lucene.store.{Directory, FilterDirectory, IOContext}
import io.circe.parser.*
import io.circe.syntax.*

case class NixieDirectory(inner: Directory) extends FilterDirectory(inner) with Logging {
}
