package ai.nixiesearch.core.nn.model

import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.{Config, FieldSchema}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.{MapRef, Queue, Semaphore}
import cats.implicits.*

case class BiEncoderCache(
    encoders: MapRef[IO, ModelHandle, Option[OnnxBiEncoder]],
    shutdownQueue: Queue[IO, IO[Unit]],
    cacheConfig: EmbeddingCacheConfig,
    lock: Semaphore[IO]
) extends Logging {
  def get(handle: ModelHandle): IO[OnnxBiEncoder] = {
    val ref = encoders(handle)
    ref.get.flatMap {
      case Some(existing) => IO.pure(existing)
      case None =>
        for {
          _       <- info(s"Loading ONNX models: $handle")
          _       <- lock.acquire *> debug("acquired lock for model loading")
          session <- OnnxSession.load(handle)
          enc = OnnxBiEncoder(session, cacheConfig)
          _ <- ref.set(Some(enc))
          _ <- shutdownQueue.offer(IO(enc.close()))
          _ <- lock.release *> debug("released lock for model loading")
        } yield {
          enc
        }
    }
  }

  def close(): IO[Unit] = shutdownQueue.tryTake.flatMap {
    case Some(next) => next *> close()
    case None       => IO.unit
  }
}

object BiEncoderCache extends Logging {
  def create(cacheConfig: EmbeddingCacheConfig): Resource[IO, BiEncoderCache] = {
    Resource.make(for {
      queue <- Queue.bounded[IO, IO[Unit]](1024)
      lock  <- Semaphore[IO](1)
      cache <- MapRef.ofConcurrentHashMap[IO, ModelHandle, OnnxBiEncoder]()
    } yield {
      BiEncoderCache(cache, queue, cacheConfig, lock)
    })(_.close())

  }
}
