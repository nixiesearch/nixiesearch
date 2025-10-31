package ai.nixiesearch.api.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams, SuggestSchema}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.config.mapping.FieldName.StringName

class SuggestSingleFieldTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(name = StringName("title"), search = SearchParams(), suggest = Some(SuggestSchema())),
      TextListFieldSchema(name = StringName("genres"), search = SearchParams(), suggest = Some(SuggestSchema()))
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
      resp.suggestions.map(_.text) shouldBe List("action", "arcade", "romance")
    }
  }
}
