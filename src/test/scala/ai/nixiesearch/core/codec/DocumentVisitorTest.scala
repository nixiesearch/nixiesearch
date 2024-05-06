package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SearchRequest
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
import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import ai.nixiesearch.util.SearchTest
import cats.data.NonEmptyList

class DocumentVisitorTest extends AnyFlatSpec with Matchers with SearchTest {
  val docs = Nil
  val mapping = IndexMapping(
    name = "test",
    fields = List(TextFieldSchema("_id"), TextFieldSchema("title"), IntFieldSchema("count"))
  )

  it should "collect doc from fields" in withIndex { store =>
    {
      val source = Document(List(TextField("_id", "1"), TextField("title", "foo"), IntField("count", 1)))
      store.indexer.addDocuments(List(source)).unsafeRunSync()
      store.indexer.flush().unsafeRunSync()
      store.searcher.sync().unsafeRunSync()

      val request = SearchRequest(MatchAllQuery(), fields = List("_id", "title", "count"))
      val docs    = store.searcher.search(request).unsafeRunSync()
      docs.hits shouldBe List(Document(source.fields :+ FloatField("_score", 1.0)))
    }
  }
}
