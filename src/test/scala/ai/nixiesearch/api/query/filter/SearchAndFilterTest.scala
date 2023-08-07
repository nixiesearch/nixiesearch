package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers

class SearchAndFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("id", filter = true),
      TextFieldSchema("color", filter = true),
      TextFieldSchema("title", search = LexicalSearch())
    )
  )
  val index = List(
    Document(List(TextField("id", "1"), TextField("color", "red"), TextField("title", "big jacket"))),
    Document(List(TextField("id", "2"), TextField("color", "white"), TextField("title", "evening dress"))),
    Document(List(TextField("id", "3"), TextField("color", "red"), TextField("title", "branded dress")))
  )

  it should "search and filter" in new Index {
    val results =
      search(query = MatchQuery("title", "dress"), filters = Filter(include = Some(TermPredicate("color", "red"))))
    results shouldBe List("3")
  }
}
