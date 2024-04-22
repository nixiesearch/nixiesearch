package ai.nixiesearch.util

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.Index
import ai.nixiesearch.index.cluster.{Indexer, Searcher}
import cats.effect.IO
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global

import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

trait LocalNixieFixture extends AnyFlatSpec {

  def withCluster(index: IndexMapping)(code: LocalNixie => Any): Unit = {
    val cluster = LocalNixie.create(index).unsafeRunSync()
    try {
      code(cluster)
    } finally cluster.close()
  }
}
