package ai.nixiesearch.core.nn.model

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.ModelFileCache.CacheKey
import cats.effect.IO
import fs2.io.file.{Files, Path as Fs2Path}
import fs2.Stream
import fs2.io.writeOutputStream

import java.io.FileOutputStream
import java.nio.file.{Path as NioPath}

case class ModelFileCache(dir: NioPath) extends Logging {
  def exists(key: CacheKey): IO[Boolean] = Files[IO].exists(Fs2Path.fromNioPath(key.resolve(dir)))

  def getIfExists(key: CacheKey): IO[Option[NioPath]] = exists(key).flatMap {
    case true  => get(key).map(path => Some(path))
    case false => IO.none
  }
  def get(key: CacheKey): IO[NioPath] =
    exists(key).flatMap {
      case false => IO.raiseError(BackendError(s"trying to read cached file $key, but it's missing"))
      case true  => IO(key.resolve(dir))
    }

  def put(key: CacheKey, bytes: Stream[IO, Byte]): IO[Unit] = {
    exists(key).flatMap {
      case true => IO.raiseError(BackendError(s"trying to write a cache file for $key, but it already exists"))
      case false =>
        for {
          modelFilePath   <- IO(key.resolve(dir))
          parent          <- IO(modelFilePath.getParent)
          parentDirExists <- Files[IO].exists(Fs2Path.fromNioPath(modelFilePath.getParent))
          _               <- IO.whenA(!parentDirExists)(Files[IO].createDirectories(Fs2Path.fromNioPath(parent)))
          parentDirIsDir  <- Files[IO].isDirectory(Fs2Path.fromNioPath(parent))
          _ <- IO.whenA(!parentDirIsDir)(
            IO.raiseError(BackendError(s"model cache dir has wrong structure: $parent should be dir, but it's a file"))
          )
          _ <- bytes.through(writeOutputStream[IO](IO(new FileOutputStream(modelFilePath.toFile)))).compile.drain
          _ <- debug(s"cached $key to $modelFilePath")
        } yield {}
    }
  }
}

object ModelFileCache extends Logging {
  case class CacheKey(ns: String, name: String, fileName: String) {
    def resolve(dir: NioPath): NioPath =
      dir.resolve(ns).resolve(name).resolve(fileName)
  }

  def create(dir: NioPath): IO[ModelFileCache] = for {
    modelCacheDir <- IO(dir.resolve("models"))
    _             <- debug(s"using $modelCacheDir as model cache dir")
    dirExists     <- Files[IO].exists(Fs2Path.fromNioPath(modelCacheDir))
    _ <- IO.whenA(!dirExists)(
      info(s"model cache dir $modelCacheDir does not exist, creating") *> Files[IO].createDirectories(
        Fs2Path.fromNioPath(modelCacheDir)
      )
    )
  } yield {
    ModelFileCache(modelCacheDir)
  }
}
