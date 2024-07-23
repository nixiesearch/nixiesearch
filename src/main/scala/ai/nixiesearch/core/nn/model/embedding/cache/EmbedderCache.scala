package ai.nixiesearch.core.nn.model.embedding.cache

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.embedding.cache.EmbedderCache.CacheKey
import cats.effect.IO

trait EmbedderCache {
  def get(key: CacheKey): IO[Option[Array[Float]]] = get(List(key)).flatMap {
    case x if x.length == 1 => IO.pure(x(0))
    case other => IO.raiseError(BackendError(s"cache impl expected to return single element, but got $other"))
  }
  def get(keys: List[CacheKey]): IO[Array[Option[Array[Float]]]]
  def put(keys: List[CacheKey], values: Array[Array[Float]]): IO[Unit]
}

object EmbedderCache {
  case class CacheKey(handle: ModelHandle, string: String)
}
