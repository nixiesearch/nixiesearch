package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.store.Store
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

trait StoreFixture extends AnyFlatSpec with BeforeAndAfterAll {
  private val pendingDeleteDirs = new ArrayBuffer[Path]()

  override def afterAll() = {
    pendingDeleteDirs.foreach(path => FileUtils.deleteDirectory(path.toFile))
    super.afterAll()
  }

  def withStore(index: IndexMapping)(code: Store => Any): Unit = {
    val store = TestLocalStore(index)
    pendingDeleteDirs.addOne(store.dir)
    try {
      code(store)
    } finally store.close()
  }
  def withStore(code: Store => Any): Unit = {
    val store = TestLocalStore()
    pendingDeleteDirs.addOne(store.dir)
    try { code(store) }
    finally store.close()
  }
}
