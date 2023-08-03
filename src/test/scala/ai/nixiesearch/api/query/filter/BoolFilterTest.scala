package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.filter.Predicate.BoolPredicate.{AndPredicate, OrPredicate}
import ai.nixiesearch.api.filter.Predicate.RangePredicate.{RangeGte, RangeGteLte}
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{FloatField, TextField}
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers

class BoolFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("id", filter = true),
      TextFieldSchema("color", filter = true),
      FloatFieldSchema("price", filter = true)
    )
  )
  val index = List(
    Document(List(TextField("id", "1"), TextField("color", "red"), FloatField("price", 10))),
    Document(List(TextField("id", "2"), TextField("color", "white"), FloatField("price", 20))),
    Document(List(TextField("id", "3"), TextField("color", "red"), FloatField("price", 30))),
    Document(List(TextField("id", "4"), TextField("color", "white"), FloatField("price", 40)))
  )

  it should "select by both filters" in new Index {
    val result =
      search(filters = Filter(include = Some(AndPredicate(List(TermPredicate("color", "red"), RangeGte("price", 20))))))
    result shouldBe List("3")
  }

  it should "do or" in new Index {
    val result =
      search(filters = Filter(include = Some(OrPredicate(List(TermPredicate("color", "red"), RangeGte("price", 30))))))
    result shouldBe List("1", "3", "4")
  }
}
