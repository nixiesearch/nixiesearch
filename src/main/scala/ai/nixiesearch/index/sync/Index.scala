package ai.nixiesearch.index.sync

import ai.nixiesearch.config
import ai.nixiesearch.config.{CacheConfig, IndexCacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedderDict
import ai.nixiesearch.index.store.StateClient
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory

trait Index extends Logging {
  def mapping: IndexMapping
  def encoders: EmbedderDict
  def master: StateClient
  def replica: StateClient
  def local: StateClient
  def directory: Directory
  def seqnum: Ref[IO, Long]
  def sync(): IO[Boolean]

  def name = mapping.name

}

object Index {
  def local(mapping: IndexMapping, cacheConfig: CacheConfig): Resource[IO, LocalIndex] = mapping.store match {
    case local: config.StoreConfig.LocalStoreConfig => LocalIndex.create(mapping, local, cacheConfig)
    case dist: config.StoreConfig.DistributedStoreConfig =>
      Resource.raiseError[IO, LocalIndex, Throwable](
        new UnsupportedOperationException("cannot open distributed index in local standalone mode")
      )
  }
  def forSearch(mapping: IndexMapping, cacheConfig: CacheConfig): Resource[IO, Index] = mapping.store match {
    case local: StoreConfig.LocalStoreConfig      => LocalIndex.create(mapping, local, cacheConfig)
    case dist: StoreConfig.DistributedStoreConfig => SlaveIndex.create(mapping, dist, cacheConfig)
  }

  def forIndexing(mapping: IndexMapping, cacheConfig: CacheConfig): Resource[IO, Index] = mapping.store match {
    case local: StoreConfig.LocalStoreConfig      => LocalIndex.create(mapping, local, cacheConfig)
    case dist: StoreConfig.DistributedStoreConfig => MasterIndex.create(mapping, dist, cacheConfig)
  }
}
