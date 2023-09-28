package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class TermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("_id", filter = true),
      TextFieldSchema("color", filter = true),
      TextFieldSchema("color2", filter = true)
    )
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("color", "red"))),
    Document(List(TextField("_id", "2"), TextField("color", "white"), TextField("color2", "light white"))),
    Document(List(TextField("_id", "3"), TextField("color", "red")))
  )

  it should "select terms" in new Index {
    val results = search(filters = Filters(include = Some(TermPredicate("color", "white"))))
    results shouldBe List("2")
  }

  it should "select terms with space" in new Index {
    val results = search(filters = Filters(include = Some(TermPredicate("color2", "light white"))))
    results shouldBe List("2")
  }

}
