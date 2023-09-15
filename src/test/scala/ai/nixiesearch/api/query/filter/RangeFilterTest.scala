package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.RangePredicate.RangeGteLte
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RangeFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("_id", filter = true),
      IntFieldSchema("count", filter = true),
      FloatFieldSchema("price", filter = true)
    )
  )
  val index = List(
    Document(List(TextField("_id", "1"), IntField("count", 10), FloatField("price", 10.0))),
    Document(List(TextField("_id", "2"), IntField("count", 15), FloatField("price", 15.0))),
    Document(List(TextField("_id", "3"), IntField("count", 20), FloatField("price", 20.0))),
    Document(List(TextField("_id", "4"), IntField("count", 30), FloatField("price", 30.0)))
  )

  "int range" should "select all on wide range" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("count", 0, 100))))
    result shouldBe List("1", "2", "3", "4")
  }

  it should "select none on out-of-range" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("count", 100, 200))))
    result shouldBe Nil
  }

  it should "select subset" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("count", 12, 22))))
    result shouldBe List("2", "3")
  }

  it should "include the borders" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("count", 10, 20))))
    result shouldBe List("1", "2", "3")
  }

  "float range" should "select subset" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("price", 12, 22))))
    result shouldBe List("2", "3")
  }
  it should "include the borders" in new Index {
    val result = search(filters = Filters(include = Some(RangeGteLte("price", 10, 20))))
    result shouldBe List("1", "2", "3")
  }
}
