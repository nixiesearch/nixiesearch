package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import io.circe.parser.*
class MatchQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "select matching documents for a single-term query" in withIndex { index =>
    {
      val docs = index.search(MatchQuery("title", "pajama"))
      docs shouldBe List("3")
    }
  }

  it should "select docs for a multi-term query and AND" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("white pajama", List("title"), Operator.AND))
      docs shouldBe Nil
    }
  }

  it should "select docs for a multi-term query and OR" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("white pajama", List("title"), Operator.OR))
      docs shouldBe List("2", "3")
    }
  }

}
