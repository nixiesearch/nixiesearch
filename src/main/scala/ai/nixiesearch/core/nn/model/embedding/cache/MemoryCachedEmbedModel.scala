package ai.nixiesearch.core.nn.model.embedding.cache

import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.cache.CachedEmbedModel.CacheRecord
import cats.effect.IO
import cats.effect.kernel.Resource
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.google.common.hash.Hashing

import java.nio.charset.{Charset, StandardCharsets}

case class MemoryCachedEmbedModel(underlying: EmbedModel, cache: Cache[Long, Array[Float]], config: MemoryCacheConfig)
    extends CachedEmbedModel {
  override val batchSize                      = 128
  lazy val hasher                             = Hashing.murmur3_128()
  def key(task: TaskType, text: String): Long =
    hasher.hashString(s"${task.name}: $text", StandardCharsets.UTF_8).asLong()

  override def get(task: EmbedModel.TaskType, docs: List[String]): IO[List[Option[Array[Float]]]] = IO {
    docs.map(doc => cache.getIfPresent(key(task, doc)))
  }

  override def put(task: EmbedModel.TaskType, docs: List[CacheRecord.EmbedRecord]): IO[Unit] = IO {
    docs.foreach(doc => cache.put(key(task, doc.text), doc.embed))
  }

}

object MemoryCachedEmbedModel extends Logging {
  def create(underlying: EmbedModel, config: MemoryCacheConfig): Resource[IO, MemoryCachedEmbedModel] =
    Resource.liftK(IO(createUnsafe(underlying, config)))

  def createUnsafe(underlying: EmbedModel, config: MemoryCacheConfig) = {
    val cache = Scaffeine().maximumSize(config.maxSize).build[Long, Array[Float]]()
    logger.info(s"Heap-based embedding cache enabled for model '${underlying.model}, max size = ${config.maxSize}'")
    MemoryCachedEmbedModel(underlying, cache, config)
  }
}
