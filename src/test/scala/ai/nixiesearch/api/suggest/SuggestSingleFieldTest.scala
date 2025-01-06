package ai.nixiesearch.api.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SuggestSchema}
import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SuggestSingleFieldTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = NoSearch, suggest = Some(SuggestSchema())),
      TextListFieldSchema(name = "genres", search = NoSearch, suggest = Some(SuggestSchema()))
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("title", "hello world"), TextListField("genres", "action", "arcade"))),
    Document(List(TextField("title", "I like hotdogs"), TextListField("genres", "action", "romance"))),
    Document(List(TextField("title", "where is my mind?"), TextListField("genres", "sports")))
  )

  it should "generate suggestions for text fields" in withIndex { nixie =>
    {
      val resp = nixie.searcher.suggest(SuggestRequest(query = "he", fields = List("title"))).unsafeRunSync()
      resp.suggestions.map(_.text) shouldBe List("hello", "hello world", "where", "where is", "where is my")
    }
  }

  it should "generate suggestions for text[] fields" in withIndex { nixie =>
    {
      val resp = nixie.searcher.suggest(SuggestRequest(query = "a", fields = List("genres"))).unsafeRunSync()
      resp.suggestions.map(_.text) shouldBe List("hello", "hello world", "where", "where is", "where is my")
    }
  }
}
