package ai.nixiesearch.core.index

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.index.IndexSnapshot
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.Document

import java.nio.file.Files
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.util.IndexFixture
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*

class IndexSnapshotTest extends AnyFlatSpec with Matchers with IndexFixture {
  val mapping = IndexMapping(
    name = "test",
    fields = List(TextFieldSchema("id"), TextFieldSchema("title"), IntFieldSchema("count"))
  )

  it should "make snapshot from directory" in withStore(mapping) { store =>
    {
      val source = Document(List(TextField("id", "1"), TextField("title", "foo"), IntField("count", 1)))
      val writer = store.writer(mapping).unsafeRunSync()
      writer.addDocuments(List(source)).unsafeRunSync()
      writer.writer.commit()
      val snap = IndexSnapshot.fromDirectory(mapping, writer.directory).unsafeRunSync()
      snap.files.size shouldBe 6
      val json = snap.asJson
      json.isObject shouldBe true
    }
  }
}
