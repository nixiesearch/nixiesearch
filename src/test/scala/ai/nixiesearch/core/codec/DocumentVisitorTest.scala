package ai.nixiesearch.core.codec

import ai.nixiesearch.api.query.MatchAllQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Paths
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Document

import java.nio.file.Path
import ai.nixiesearch.config.FieldSchema.IntFieldSchema

import java.nio.file.Files
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.MatchAllDocsQuery
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.Field.IntField
import ai.nixiesearch.util.IndexFixture

class DocumentVisitorTest extends AnyFlatSpec with Matchers with IndexFixture {
  val mapping = IndexMapping(
    name = "test",
    fields = List(TextFieldSchema("id"), TextFieldSchema("title"), IntFieldSchema("count"))
  )

  it should "collect doc from fields" in withStore(mapping) { store =>
    {
      val source = Document(List(TextField("id", "1"), TextField("title", "foo"), IntField("count", 1)))
      val writer = store.writer(mapping).unsafeRunSync()
      writer.addDocuments(List(source)).unsafeRunSync()
      writer.writer.commit()
      val reader = store.reader(mapping.name).unsafeRunSync().get

      val docs = reader.search(MatchAllQuery(), List("id", "title", "count"), 10).unsafeRunSync()
      docs.hits shouldBe List(source)
    }
  }
}
