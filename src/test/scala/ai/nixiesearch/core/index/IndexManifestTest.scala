package ai.nixiesearch.core.index

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.Document

import java.nio.file.Files
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.util.LocalNixieFixture
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.apache.commons.codec.digest.DigestUtils
import org.apache.lucene.store.{ByteBuffersDirectory, IOContext}

import scala.util.Random

class IndexManifestTest extends AnyFlatSpec with Matchers with LocalNixieFixture {
  val mapping = IndexMapping(
    name = "test",
    fields = List(TextFieldSchema("_id"), TextFieldSchema("title"), IntFieldSchema("count"))
  )

  it should "make snapshot from directory" in withCluster(mapping) { store =>
    {
      val source = Document(List(TextField("_id", "1"), TextField("title", "foo"), IntField("count", 1)))
      store.indexer.index(mapping.name, List(source)).unsafeRunSync()
      
      store.indexer.flush(mapping.name).unsafeRunSync()
      val snap = IndexManifest.fromDirectory(mapping, index.directory).unsafeRunSync()
      snap.files.size shouldBe 6
      val json = snap.asJson
      json.isObject shouldBe true
    }
  }
}
