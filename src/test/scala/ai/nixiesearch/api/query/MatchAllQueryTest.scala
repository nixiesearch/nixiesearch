package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MatchAllQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val docs    = List(
    Document(List(IdField("_id", "1"), TextField("title", "red dress"))),
    Document(List(IdField("_id", "2"), TextField("title", "white dress"))),
    Document(List(IdField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "select all docs" in withIndex { index =>
    {
      val request = SearchRequest(query = MatchAllQuery())
      val docs    = index.searcher.search(request).unsafeRunSync()
      val ids     = docs.hits.flatMap(_.fields.collect { case IdField(_, text) => text })
      ids shouldBe List("1", "2", "3")
    }
  }

  it should "limit the number of docs" in withIndex { index =>
    {
      val request = SearchRequest(query = MatchAllQuery(), size = 1)
      val docs    = index.searcher.search(request).unsafeRunSync()
      val ids     = docs.hits.flatMap(_.fields.collect { case IdField(_, text) => text })
      ids shouldBe List("1")
    }
  }
}
