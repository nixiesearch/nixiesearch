package ai.nixiesearch.index.store

import cats.effect.IO
import org.apache.lucene.store.{Directory, FilterDirectory}

abstract class AsyncDirectory(inner: Directory) extends FilterDirectory(inner) {
  def closeAsync(): IO[Unit] = IO(inner.close())
}

object AsyncDirectory {
  def wrap(dir: Directory): AsyncDirectory = new AsyncDirectory(dir) {}
}
