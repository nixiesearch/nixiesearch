package ai.nixiesearch.api.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestSchema}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SuggestDeduplicationTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = NoSearch, suggest = Some(SuggestSchema()))
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("title", "hello world"))),
    Document(List(TextField("title", "world hello"))),
    Document(List(TextField("title", "world crazy hello")))
  )

  it should "dedup suggestions" in withIndex { nixie =>
    {
      val resp = nixie.searcher
        .suggest(
          SuggestRequest(
            query = "he",
            fields = List("title")
          )
        )
        .unsafeRunSync()
      resp.suggestions
        .map(_.text) shouldBe List("hello", "hello world", "crazy hello", "world crazy hello", "world hello")
    }
  }

}
