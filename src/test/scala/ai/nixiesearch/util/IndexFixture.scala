package ai.nixiesearch.util

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.IndexRegistry
import ai.nixiesearch.index.store.Store
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

trait IndexFixture extends AnyFlatSpec with BeforeAndAfterAll {
  private val pendingDeleteDirs = new ArrayBuffer[Path]()

  override def afterAll() = {
    pendingDeleteDirs.foreach(path => FileUtils.deleteDirectory(path.toFile))
    super.afterAll()
  }

  def withStore(index: IndexMapping)(code: IndexRegistry => Any): Unit = {
    val dir = Files.createTempDirectory("nixie")
    dir.toFile.deleteOnExit()
    val (registry, shutdown) =
      IndexRegistry.create(LocalStoreConfig(LocalStoreUrl(dir.toString)), List(index)).allocated.unsafeRunSync()

    pendingDeleteDirs.addOne(dir)

    try {
      code(registry)
    } finally registry.close().unsafeRunSync()
  }
  def withStore(code: IndexRegistry => Any): Unit = {
    val dir = Files.createTempDirectory("nixie")
    dir.toFile.deleteOnExit()
    val (registry, shutdown) =
      IndexRegistry.create(LocalStoreConfig(LocalStoreUrl(dir.toString)), Nil).allocated.unsafeRunSync()
    pendingDeleteDirs.addOne(dir)
    try { code(registry) }
    finally registry.close().unsafeRunSync()
  }
}
