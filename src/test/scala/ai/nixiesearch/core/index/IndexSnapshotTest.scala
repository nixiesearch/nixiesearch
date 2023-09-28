package ai.nixiesearch.core.index

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.index.IndexSnapshot
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.Document

import java.nio.file.Files
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.util.LocalIndexFixture
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.apache.commons.codec.digest.DigestUtils
import org.apache.lucene.store.{ByteBuffersDirectory, IOContext}

import scala.util.Random

class IndexSnapshotTest extends AnyFlatSpec with Matchers with LocalIndexFixture {
  val mapping = IndexMapping(
    name = "test",
    fields = List(TextFieldSchema("_id"), TextFieldSchema("title"), IntFieldSchema("count"))
  )

  it should "compute md5 in a stream" in {
    val dir  = new ByteBuffersDirectory()
    val blob = Random.nextBytes(64 * 1024)
    val out  = dir.createOutput("test.dat", IOContext.DEFAULT)
    out.writeBytes(blob, blob.length)
    out.close()
    val md5stream = IndexSnapshot.md5(dir, "test.dat").unsafeRunSync()
    val md5mem    = DigestUtils.md5Hex(blob)
    md5mem shouldBe md5stream
  }

  it should "make snapshot from directory" in withStore(mapping) { store =>
    {
      val source = Document(List(TextField("_id", "1"), TextField("title", "foo"), IntField("count", 1)))
      val index  = store.index(mapping.name).unsafeRunSync().get
      index.addDocuments(List(source)).unsafeRunSync()
      index.writer.commit()
      val snap = IndexSnapshot.fromDirectory(mapping, index.directory).unsafeRunSync()
      snap.files.size shouldBe 6
      val json = snap.asJson
      json.isObject shouldBe true
    }
  }
}
