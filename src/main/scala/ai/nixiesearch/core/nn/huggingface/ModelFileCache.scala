package ai.nixiesearch.core.nn.huggingface

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.huggingface.ModelFileCache.CacheKey
import cats.effect.IO
import fs2.Stream
import fs2.io.file.{Files, Path as Fs2Path}
import fs2.io.writeOutputStream

import java.io.FileOutputStream
import java.nio.file.Path as NioPath

sealed trait ModelFileCache extends Logging {
  def exists(key: CacheKey): IO[Boolean]
  def getIfExists(key: CacheKey): IO[Option[NioPath]]
  def get(key: CacheKey): IO[NioPath]
  def put(key: CacheKey, bytes: Stream[IO, Byte]): IO[Unit]
}

object ModelFileCache extends Logging {

  case class LocalModelFileCache(dir: NioPath) extends ModelFileCache {
    override def exists(key: CacheKey): IO[Boolean] = Files[IO].exists(Fs2Path.fromNioPath(key.resolve(dir)))

    override def getIfExists(key: CacheKey): IO[Option[NioPath]] = exists(key).flatMap {
      case true  => get(key).map(path => Some(path))
      case false => IO.none
    }

    override def get(key: CacheKey): IO[NioPath] =
      exists(key).flatMap {
        case false => IO.raiseError(BackendError(s"trying to read cached file $key, but it's missing"))
        case true  => IO(key.resolve(dir))
      }

    override def put(key: CacheKey, bytes: Stream[IO, Byte]): IO[Unit] = {
      exists(key).flatMap {
        case true  => IO.raiseError(BackendError(s"trying to write a cache file for $key, but it already exists"))
        case false =>
          for {
            tempKey         <- IO(key.copy(fileName = key.fileName + ".tmp"))
            modelFilePath   <- IO(tempKey.resolve(dir))
            parent          <- IO(modelFilePath.getParent)
            parentDirExists <- Files[IO].exists(Fs2Path.fromNioPath(modelFilePath.getParent))
            _               <- IO.whenA(!parentDirExists)(Files[IO].createDirectories(Fs2Path.fromNioPath(parent)))
            parentDirIsDir  <- Files[IO].isDirectory(Fs2Path.fromNioPath(parent))
            _               <- IO.whenA(!parentDirIsDir)(
              IO.raiseError(
                BackendError(s"model cache dir has wrong structure: $parent should be dir, but it's a file")
              )
            )
            _ <- bytes.through(writeOutputStream[IO](IO(new FileOutputStream(modelFilePath.toFile)))).compile.drain
            _ <- debug(s"cached $key to $modelFilePath")
            _ <- Files[IO].move(Fs2Path.fromNioPath(modelFilePath), Fs2Path.fromNioPath(key.resolve(dir)))
          } yield {}
      }
    }
  }
  object NoopModelFileCache extends ModelFileCache {
    override def exists(key: CacheKey): IO[Boolean] = IO.pure(false)
    override def get(key: CacheKey): IO[NioPath]    = IO.raiseError(BackendError("model file caching not enabled"))
    override def getIfExists(key: CacheKey): IO[Option[NioPath]]       = IO.pure(None)
    override def put(key: CacheKey, bytes: Stream[IO, Byte]): IO[Unit] = IO.unit
  }

  case class CacheKey(ns: String, name: String, fileName: String) {
    def resolve(dir: NioPath): NioPath =
      dir.resolve(ns).resolve(name).resolve(fileName)
  }

  def create(inferenceConfig: InferenceConfig, dir: NioPath): IO[ModelFileCache] =
    if (inferenceConfig.hasLocalModels) {
      for {
        modelCacheDir <- IO(dir.resolve("models"))
        _             <- debug(s"using $modelCacheDir as model cache dir")
        dirExists     <- Files[IO].exists(Fs2Path.fromNioPath(modelCacheDir))
        _             <- IO.whenA(!dirExists)(
          info(s"model cache dir $modelCacheDir does not exist, creating") *> Files[IO].createDirectories(
            Fs2Path.fromNioPath(modelCacheDir)
          )
        )
      } yield {
        LocalModelFileCache(modelCacheDir)
      }
    } else {
      info("No local inference models found, skipping model cache init.") *> IO.pure(NoopModelFileCache)
    }
}
