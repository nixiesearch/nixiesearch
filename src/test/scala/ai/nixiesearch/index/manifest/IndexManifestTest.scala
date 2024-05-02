package ai.nixiesearch.index.manifest

import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.util.TestIndexMapping
import cats.effect.unsafe.implicits.global
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.ByteBuffersDirectory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IndexManifestTest extends AnyFlatSpec with Matchers {
  it should "read none for missing manifest" in {
    val dir = new ByteBuffersDirectory()
    val mf  = IndexManifest.read(dir).unsafeRunSync()
    mf shouldBe None
  }

  it should "write+read manifest" in {
    val dir       = new ByteBuffersDirectory()
    val reference = IndexManifest(TestIndexMapping(), List(IndexFile("foo", 1L, 1L)), 0L)
    IndexManifest.write(dir, reference).unsafeRunSync()
    IndexManifest.write(dir, reference).unsafeRunSync()
    val decoded = IndexManifest.read(dir).unsafeRunSync()
    decoded shouldBe Some(reference)
  }

}
