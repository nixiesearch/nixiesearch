package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.{MatchQuery, MultiMatchQuery}
import ai.nixiesearch.api.query.retrieve.MatchQuery.Operator
import ai.nixiesearch.api.query.retrieve.MultiMatchQuery.BestFieldsQuery
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class MatchQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val docs    = List(
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

}
