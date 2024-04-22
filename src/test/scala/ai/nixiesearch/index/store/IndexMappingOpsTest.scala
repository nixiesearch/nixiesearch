package ai.nixiesearch.index.store

import ai.nixiesearch.index.IndexMappingOps
import ai.nixiesearch.util.TestIndexMapping
import org.apache.lucene.store.ByteBuffersDirectory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.NoSuchFileException
import scala.util.{Failure, Try}

class IndexMappingOpsTest extends AnyFlatSpec with Matchers {
  import ai.nixiesearch.index.IndexMappingOps.*

  it should "write-load index mapping" in {
    val dir  = new ByteBuffersDirectory()
    val orig = TestIndexMapping()
    orig.writeToDirectory(dir).unsafeRunSync()
    val decoded = IndexMappingOps.loadFromDirectory(dir).unsafeRunSync()
    decoded shouldBe orig
  }

  it should "fail on missing mapping" in {
    val dir     = new ByteBuffersDirectory()
    val decoded = Try(IndexMappingOps.loadFromDirectory(dir).unsafeRunSync())
    decoded shouldBe a[Failure[NoSuchFileException]]
  }
}
