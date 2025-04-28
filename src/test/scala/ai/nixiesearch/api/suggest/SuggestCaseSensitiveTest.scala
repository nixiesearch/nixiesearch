package ai.nixiesearch.api.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams, SuggestSchema}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.config.mapping.FieldName.StringName

class SuggestCaseSensitiveTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(name = StringName("title"), search = SearchParams(), suggest = Some(SuggestSchema()))
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("title", "Hello"))),
    Document(List(TextField("title", "HELLO"))),
    Document(List(TextField("title", "HELLo")))
  )

  it should "suggest with caseSensitive=false" in withIndex { nixie =>
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
        .map(_.text) shouldBe List("hello")

    }
  }
}
