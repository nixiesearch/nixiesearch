package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MatchQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val index = List(
    Document(List(TextField("id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("id", "3"), TextField("title", "red pajama")))
  )

  it should "select matching documents for a single-term query" in new Index {
    val docs = searcher.search(MultiMatchQuery("pajama", List("title")), List("id"), 10).unsafeRunSync()
    val ids  = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("3")
  }

  it should "select docs for a multi-term query and AND" in new Index {
    val docs =
      searcher.search(MultiMatchQuery("white pajama", List("title"), Operator.AND), List("id"), 10).unsafeRunSync()
    val ids = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe Nil
  }

  it should "select docs for a multi-term query and OR" in new Index {
    val docs =
      searcher.search(MultiMatchQuery("white pajama", List("title"), Operator.OR), List("id"), 10).unsafeRunSync()
    val ids = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("2", "3")
  }
}
