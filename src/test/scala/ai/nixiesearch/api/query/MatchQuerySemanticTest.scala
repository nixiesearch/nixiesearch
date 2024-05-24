package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, NoSearch}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{TextField, TextListField}
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MatchQuerySemanticTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = HybridSearch()),
      TextListFieldSchema(name = "cat", search = NoSearch, filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"), TextListField("cat", "a", "b"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"), TextListField("cat", "b", "c"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama"), TextListField("cat", "c", "a")))
  )

  it should "search for similar docs" in withIndex { index =>
    {
      val response = index.search(MatchQuery("title", "white pajama"))
      response shouldBe List("2", "3", "1")
    }
  }

  it should "search and filter" in withIndex { index =>
    {
      val response =
        index.search(MatchQuery("title", "white pajama"), filters = Filters(include = Some(TermPredicate("cat", "b"))))
      response shouldBe List("2", "1")
    }
  }
}
