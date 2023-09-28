package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.search.Searcher
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MatchAllQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "select all docs" in new Index {
    val request = SearchRequest(query = MatchAllQuery())
    val docs    = Searcher.search(request, index).unsafeRunSync()
    val ids     = docs.hits.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("1", "2", "3")
  }

  it should "limit the number of docs" in new Index {
    val request = SearchRequest(query = MatchAllQuery(), size = 1)
    val docs    = Searcher.search(request, index).unsafeRunSync()
    val ids     = docs.hits.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("1")
  }
}
