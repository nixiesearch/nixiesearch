package ai.nixiesearch.core.nn.model.embedding.cache
import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.core.nn.model.embedding.cache.EmbedderCache.CacheKey
import cats.effect.IO
import cats.effect.kernel.Resource
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class HeapEmbedderCache(cache: Cache[CacheKey, Array[Float]]) extends EmbedderCache {
  override def get(keys: List[CacheKey]): IO[Array[Option[Array[Float]]]] = IO {
    keys.map(key => cache.getIfPresent(key)).toArray
  }
  override def put(keys: List[CacheKey], values: Array[Array[Float]]): IO[Unit] = IO {
    keys.zip(values).foreach { case (key, value) =>
      cache.put(key, value)
    }
  }
}

object HeapEmbedderCache {
  def create(config: EmbeddingCacheConfig): Resource[IO, HeapEmbedderCache] =
    Resource.liftK(IO(createUnsafe(config)))

  def createUnsafe(config: EmbeddingCacheConfig) = {
    val cache = Scaffeine().maximumSize(config.maxSize).build[CacheKey, Array[Float]]()
    HeapEmbedderCache(cache)
  }
}
