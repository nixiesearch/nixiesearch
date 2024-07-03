package ai.nixiesearch.index.sync

import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp.{Add, Del}
import ai.nixiesearch.index.manifest.IndexManifest.{ChangedFileOp, IndexFile}
import ai.nixiesearch.index.sync.IndexManifestDiffTest.TestManifest
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class IndexManifestDiffTest extends AnyFlatSpec with Matchers {

  it should "dump all files if target is empty" in {
    val source = TestManifest(List(IndexFile("a", 100L)))
    val result = source.diff(None).unsafeRunSync()
    result shouldBe List(Add("a"), Add(IndexManifest.MANIFEST_FILE_NAME))
  }

  it should "update only manifest when nothing changed" in {
    val source = TestManifest(List(IndexFile("a", 100L)))
    val result = source.diff(Some(source)).unsafeRunSync()
    result shouldBe List(Add(IndexManifest.MANIFEST_FILE_NAME))
  }

  it should "add new files" in {
    val source = TestManifest(List(IndexFile("a", 100L), IndexFile("b", 100L)))
    val dest   = TestManifest(List(IndexFile("a", 100L)))
    val result = source.diff(Some(dest)).unsafeRunSync()
    result shouldBe List(Add("b"), Add(IndexManifest.MANIFEST_FILE_NAME))
  }

  it should "del removed files" in {
    val source = TestManifest(List(IndexFile("a", 100L)))
    val dest   = TestManifest(List(IndexFile("a", 100L), IndexFile("b", 100L)))
    val result = source.diff(Some(dest)).unsafeRunSync()
    result shouldBe List(Del("b"), Add(IndexManifest.MANIFEST_FILE_NAME))
  }

  it should "replace changed files" in {
    val source = TestManifest(List(IndexFile("a", 200L), IndexFile("b", 100L)))
    val dest   = TestManifest(List(IndexFile("a", 100L), IndexFile("b", 100L)))
    val result = source.diff(Some(dest)).unsafeRunSync()
    result shouldBe List(Add("a"), Add(IndexManifest.MANIFEST_FILE_NAME))
  }

}

object IndexManifestDiffTest {

  object TestManifest {
    def apply(files: List[IndexFile]): IndexManifest = IndexManifest(TestIndexMapping(), files, 0L)
  }
}
