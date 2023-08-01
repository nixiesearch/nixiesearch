package ai.nixiesearch.index.store

import ai.nixiesearch.config.StoreConfig.S3StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.store.Store.StoreReader
import cats.effect.IO
import org.apache.lucene.index.IndexReader

case class S3Store(config: S3StoreConfig) extends Store {
  override def reader(index: IndexMapping): IO[Option[StoreReader]] = ???

  override def writer(index: IndexMapping): IO[Store.StoreWriter] = ???
}
