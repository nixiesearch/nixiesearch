package ai.nixiesearch.core.index.store

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.core.Document
import ai.nixiesearch.index.store.LocalStore
import ai.nixiesearch.util.{TestDocument, TestIndexMapping}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.MatchAllDocsQuery

import java.io.File
import java.nio.file.Files
import scala.util.Random

class LocalStoreTest extends AnyFlatSpec with Matchers {
  val index = TestIndexMapping()

  it should "open/close store" in {
    val dir = Files.createTempDirectory("nixie")
    val (store, killSwitch) = LocalStore
      .create(LocalStoreConfig(LocalStoreUrl(dir.toString)), List(index))
      .allocated
      .unsafeRunSync()
    killSwitch.unsafeRunSync()
  }

  it should "open store and write/read doc" in {
    val dir = Files.createTempDirectory("nixie")
    val (store, killSwitch) = LocalStore
      .create(LocalStoreConfig(LocalStoreUrl(dir.toString)), List(index))
      .allocated
      .unsafeRunSync()
    val writer = store.writer(index).unsafeRunSync()
    val doc    = TestDocument()
    writer.addDocuments(List(doc))
    writer.writer.commit()
    val readerMaybe = store.reader(index).unsafeRunSync()
    readerMaybe.isDefined shouldBe true
    val reader = readerMaybe.get
    val docs   = reader.search(new MatchAllDocsQuery(), List("id", "title", "price"), 10).unsafeRunSync()
    docs shouldBe List(doc)
    killSwitch.unsafeRunSync()
  }
}
