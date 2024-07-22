package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.{Config, FieldSchema}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.{MapRef, Queue, Semaphore}
import cats.implicits.*
import com.github.blemale.scaffeine.Cache

case class BiEncoderCache(encoders: Map[ModelHandle, OnnxBiEncoder]) extends Logging {
  def encode(handle: ModelHandle, doc: String): IO[Array[Float]] = IO(encoders.get(handle)).flatMap {
    case None          => IO.raiseError(new Exception(s"cannot get ONNX model $handle"))
    case Some(encoder) => encoder.embed(doc)
  }
  def encode(handle: ModelHandle, docs: List[String]): IO[Array[Array[Float]]] = IO(encoders.get(handle)).flatMap {
    case None          => IO.raiseError(new Exception(s"cannot get ONNX model $handle"))
    case Some(encoder) => encoder.embed(docs.toArray)
  }

  def close(): IO[Unit] = encoders.toList
    .map { case (handle, model) =>
      info(s"closing model $handle") *> IO(model.close())
    }
    .sequence
    .void
}

object BiEncoderCache extends Logging {
  case class CacheKey(handle: ModelHandle, string: String)
  def create(handles: List[ModelHandle], cacheConfig: EmbeddingCacheConfig): Resource[IO, BiEncoderCache] = {
    val make = for {
      encoders <- handles
        .map(handle =>
          info(s"loading ONNX model $handle") *> OnnxSession
            .load(handle)
            .map(session => handle -> OnnxBiEncoder(session, cacheConfig))
        )
        .sequence
        .map(list => list.toMap)
    } yield {
      BiEncoderCache(encoders)
    }
    Resource.make(make)(handles => handles.close())
  }
}
