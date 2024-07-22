package ai.nixiesearch.core.nn.model

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.nn.model.ModelCache.CacheKey
import cats.effect.IO
import fs2.io.file.{Files, Path as Fs2Path}
import fs2.Stream
import fs2.io.writeOutputStream

import java.io.FileOutputStream
import java.nio.file.{Paths, Path as NioPath}

case class ModelCache(dir: NioPath) {
  def exists(key: CacheKey): IO[Boolean] =
    Files[IO].exists(Fs2Path.fromNioPath(key.resolve(dir)))
  def get(key: CacheKey): IO[NioPath] =
    exists(key).flatMap {
      case false => IO.raiseError(BackendError(s"trying to read cached file $key, but it's missing"))
      case true  => IO(key.resolve(dir))
    }
  def put(key: CacheKey, bytes: Stream[IO, Byte]): IO[Unit] = {
    exists(key).flatMap {
      case true => IO.raiseError(BackendError(s"trying to write a cache file for $key, but it already exists"))
      case false =>
        bytes.through(writeOutputStream[IO](IO(new FileOutputStream(key.resolve(dir).toFile)))).compile.drain
    }

  }
}

object ModelCache {
  case class CacheKey(ns: String, name: String, fileName: String) {
    def resolve(dir: NioPath): NioPath =
      dir.resolve(ns).resolve(name).resolve(fileName)
  }

  def apply(config: CacheConfig) = new ModelCache(Paths.get(config.dir))
}
