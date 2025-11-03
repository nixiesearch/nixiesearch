package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.{BoolQuery, MatchQuery}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, TextField}
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class BoolQueryTest extends SearchTest with Matchers {
  val mapping   = TestIndexMapping()
  lazy val docs = List(
    Document(List(IdField("_id", "1"), TextField("title", "red dress"))),
    Document(List(IdField("_id", "2"), TextField("title", "white dress"))),
    Document(List(IdField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "select docs with must" in withIndex { index =>
    {
      val docs = index.search(BoolQuery(must = List(MatchQuery("title", "pajama"), MatchQuery("title", "red"))))
      docs shouldBe List("3")
    }
  }

  it should "select docs with should" in withIndex { index =>
    {
      val docs = index.search(BoolQuery(should = List(MatchQuery("title", "pajama"), MatchQuery("title", "red"))))
      docs shouldBe List("3", "1")
    }
  }

  it should "select docs with should+not" in withIndex { index =>
    {
      val docs = index.search(
        BoolQuery(
          should = List(MatchQuery("title", "pajama"), MatchQuery("title", "red")),
          must_not = List(MatchQuery("title", "dress"))
        )
      )
      docs shouldBe List("3")
    }
  }

}
