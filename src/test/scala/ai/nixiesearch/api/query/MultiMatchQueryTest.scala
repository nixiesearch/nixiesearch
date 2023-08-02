package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MultiMatchQueryTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(name = "id"),
      TextFieldSchema(name = "title", search = LexicalSearch(), sort = true),
      TextFieldSchema(name = "desc", search = LexicalSearch(), sort = true)
    )
  )
  val index = List(
    Document(List(TextField("id", "1"), TextField("title", "dress"), TextField("desc", "red"))),
    Document(List(TextField("id", "2"), TextField("title", "dress"), TextField("desc", "white"))),
    Document(List(TextField("id", "3"), TextField("title", "pajama"), TextField("desc", "red")))
  )

  it should "select matching documents for a single-term query" in new Index {
    val docs = searcher.search(MultiMatchQuery("pajama", List("title", "desc")), List("id"), 10).unsafeRunSync()
    val ids  = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("3")
  }

  it should "select docs for a multi-term query and AND" in new Index {
    val docs =
      searcher
        .search(MultiMatchQuery("white pajama", List("title", "desc"), Operator.AND), List("id"), 10)
        .unsafeRunSync()
    val ids = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe Nil
  }

  it should "select docs for a multi-term query and OR" in new Index {
    val docs =
      searcher
        .search(MultiMatchQuery("white pajama", List("title", "desc"), Operator.OR), List("id"), 10)
        .unsafeRunSync()
    val ids = docs.flatMap(_.fields.collect { case TextField(_, text) => text })
    ids shouldBe List("2", "3")
  }

}
