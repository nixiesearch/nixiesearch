package ai.nixiesearch.index.sync

import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.StateClient
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory

trait ReplicatedIndex extends Logging {
  def mapping: IndexMapping
  def encoders: BiEncoderCache
  def master: StateClient
  def replica: StateClient
  def local: StateClient
  def directory: Directory
  def seqnum: Ref[IO,Long]
  def sync(): IO[Unit]
  def close(): IO[Unit]

  def name = mapping.name
}

object ReplicatedIndex {
  def create(mapping: IndexMapping, cache: CacheConfig): Resource[IO, ReplicatedIndex] = mapping.store match {
    case local: StoreConfig.LocalStoreConfig      => LocalIndex.create(mapping, local, cache)
    case dist: StoreConfig.DistributedStoreConfig => ???
  }
}
