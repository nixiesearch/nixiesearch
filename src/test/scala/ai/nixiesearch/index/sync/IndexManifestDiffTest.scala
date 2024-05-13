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
  it should "die if there's no manifest" in {
    val source = TestManifest(List(IndexFile("a", 0L)))
    an[Exception] shouldBe thrownBy {
      source.diff(None).unsafeRunSync()
    }
  }

  it should "dump all files if target is empty" in {
    val source = TestManifest(List(IndexFile("a", 0L), IndexFile(IndexManifest.MANIFEST_FILE_NAME, 0L)))
    val result = source.diff(None).unsafeRunSync()
    result shouldBe List(Add("a"), Add(IndexManifest.MANIFEST_FILE_NAME))
  }

}

object IndexManifestDiffTest {

  object TestManifest {
    def apply(files: List[IndexFile]): IndexManifest = IndexManifest(TestIndexMapping(), files, 0L)
  }
}
