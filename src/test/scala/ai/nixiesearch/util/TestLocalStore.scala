package ai.nixiesearch.util

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.store.rw.{StoreReader, StoreWriter}
import ai.nixiesearch.index.store.{LocalStore, Store}
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.nio.file.{Files, Path}

case class TestLocalStore(local: LocalStore, killSwitch: IO[Unit], dir: Path) extends Store {
  override def reader(index: IndexMapping): IO[Option[StoreReader]] = local.reader(index)

  override def writer(index: IndexMapping): IO[StoreWriter] = local.writer(index)

  override def mapping(indexName: String): IO[Option[IndexMapping]] = local.mapping(indexName)

  override def refresh(index: IndexMapping): IO[Unit] = local.refresh(index)

  override def config: StoreConfig = local.config

  def close(): Unit = {
    killSwitch.unsafeRunSync()
    dir.toFile.delete()
  }
}

object TestLocalStore {
  def apply(index: IndexMapping = TestIndexMapping()): TestLocalStore = {
    val dir = Files.createTempDirectory("nixie")
    dir.toFile.deleteOnExit()
    val (store, killSwitch) = LocalStore
      .create(LocalStoreConfig(LocalStoreUrl(dir.toString)), List(index))
      .allocated
      .unsafeRunSync()
    TestLocalStore(store, killSwitch, dir)
  }
}
