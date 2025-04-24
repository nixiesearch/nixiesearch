package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams

class SearchAndFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      TextFieldSchema(StringName("color"), filter = true),
      TextFieldSchema(StringName("title"), search = SearchParams(lexical = Some(LexicalParams())))
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("color", "red"), TextField("title", "big jacket"))),
    Document(List(TextField("_id", "2"), TextField("color", "white"), TextField("title", "evening dress"))),
    Document(List(TextField("_id", "3"), TextField("color", "red"), TextField("title", "branded dress")))
  )

  it should "search and filter" in withIndex { index =>
    {
      val results =
        index.search(
          query = MatchQuery("title", "dress"),
          filters = Some(Filters(include = Some(TermPredicate("color", "red"))))
        )
      results shouldBe List("3")
    }
  }
}
