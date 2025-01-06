package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{RangePredicate, TermPredicate}
import ai.nixiesearch.config.FieldSchema.{DateFieldSchema, DateTimeFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.FiniteRange.{Higher, Lower, RangeValue}
import ai.nixiesearch.core.field.{DateField, DateTimeField, TextField}
import ai.nixiesearch.util.SearchTest
import io.circe.Json
import org.scalatest.matchers.should.Matchers

class DateTimeFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema("_id", filter = true),
      DateTimeFieldSchema("dt", filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z"))),
    Document(List(TextField("_id", "2"), DateTimeField.applyUnsafe("dt", "2024-02-01T00:00:00Z"))),
    Document(List(TextField("_id", "3"), DateTimeField.applyUnsafe("dt", "2024-03-01T00:00:00Z")))
  )

  it should "filter over term match" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("dt", "2024-02-01T00:00:00Z")))))
      results shouldBe List("2")
    }
  }

  it should "filter over gt match" in withIndex { index =>
    {
      val days = DateTimeField.parseString("2024-02-01T00:00:00Z").toOption.get
      val results = index.search(filters =
        Some(
          Filters(include = Some(RangePredicate("dt", Lower.Gt(RangeValue(BigDecimal(days), Json.fromString("x"))))))
        )
      )
      results shouldBe List("3")
    }
  }

  it should "filter over gt/lt match" in withIndex { index =>
    {
      val days1 = DateTimeField.parseString("2024-01-01T00:00:00Z").toOption.get
      val days2 = DateTimeField.parseString("2024-03-01T00:00:00Z").toOption.get
      val results = index.search(filters =
        Some(
          Filters(include =
            Some(
              RangePredicate(
                "dt",
                Lower.Gt(RangeValue(BigDecimal(days1), Json.fromString("x"))),
                Higher.Lt(RangeValue(BigDecimal(days2), Json.fromString("x")))
              )
            )
          )
        )
      )
      results shouldBe List("2")
    }
  }

}
