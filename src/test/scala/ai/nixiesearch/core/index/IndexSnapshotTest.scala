package ai.nixiesearch.core.index

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.index.IndexSnapshot
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.Document
import java.nio.file.Files
import ai.nixiesearch.config.IndexMapping
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.index.IndexBuilder
import cats.effect.unsafe.implicits.global

class IndexSnapshotTest extends AnyFlatSpec with Matchers {
  it should "make snapshot from directory" in {
    val source = Document(List(TextField("id", "1"), TextField("title", "foo"), IntField("count", 1)))
    val tmp    = Files.createTempDirectory("test")
    val mapping = IndexMapping(
      name = "test",
      fields = List(TextFieldSchema("id"), TextFieldSchema("title"), IntFieldSchema("count"))
    )
    val writer = IndexBuilder.open(tmp, mapping)
    writer.addDocuments(List(source))
    writer.writer.commit()
    val snap = IndexSnapshot.fromDirectory(writer.dir).unsafeRunSync()
    val br   = 1
  }
}
