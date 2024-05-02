package ai.nixiesearch.index.store

import ai.nixiesearch.index.manifest.IndexManifest
import cats.effect.IO
import fs2.Stream

trait StateClient {
  def manifest(): IO[IndexManifest]
  def read(fileName: String): Stream[IO, Byte]
  def write(fileName: String, stream: Stream[IO, Byte]): IO[Unit]
  def delete(fileName: String): IO[Unit]
  def close(): IO[Unit]
}

object StateClient {
  enum StateError extends Exception {
    case FileMissingError(file: String) extends StateError
    case FileExistsError(file: String) extends StateError
  }
}
