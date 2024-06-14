package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Document
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import ai.nixiesearch.util.SearchTest

class DocumentVisitorTest extends AnyFlatSpec with Matchers with SearchTest {
  val docs = Nil
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(TextFieldSchema("_id"), TextFieldSchema("title"), IntFieldSchema("count")),
    store = LocalStoreConfig(MemoryLocation())
  )

  it should "collect doc from fields" in withIndex { store =>
    {
      val source = Document(List(TextField("_id", "1"), TextField("title", "foo"), IntField("count", 1)))
      store.indexer.addDocuments(List(source)).unsafeRunSync()
      store.indexer.flush().unsafeRunSync()
      store.indexer.index.sync().unsafeRunSync()
      store.searcher.sync().unsafeRunSync()

      val request = SearchRequest(MatchAllQuery(), fields = List("_id", "title", "count"))
      val docs    = store.searcher.search(request).unsafeRunSync()
      docs.hits shouldBe List(Document(source.fields :+ FloatField("_score", 1.0)))
    }
  }
}
