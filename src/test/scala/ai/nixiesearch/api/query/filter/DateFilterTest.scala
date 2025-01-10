package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{RangePredicate, TermPredicate}
import ai.nixiesearch.config.FieldSchema.{DateFieldSchema, IntFieldSchema, LongFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.FiniteRange.*
import ai.nixiesearch.core.field.{DateField, IntField, LongField, TextField}
import ai.nixiesearch.util.SearchTest
import io.circe.Json
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class DateFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      DateFieldSchema(StringName("date"), filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), DateField.applyUnsafe("date", "2024-01-01"))),
    Document(List(TextField("_id", "2"), DateField.applyUnsafe("date", "2024-02-01"))),
    Document(List(TextField("_id", "3"), DateField.applyUnsafe("date", "2024-03-01")))
  )

  it should "filter over term match" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("date", "2024-02-01")))))
      results shouldBe List("2")
    }
  }

  it should "filter over gt match" in withIndex { index =>
    {
      val days = DateField.parseString("2024-02-01").toOption.get
      val results = index.search(filters =
        Some(
          Filters(include = Some(RangePredicate("date", Lower.Gt(RangeValue(BigDecimal(days), Json.fromString("x"))))))
        )
      )
      results shouldBe List("3")
    }
  }

  it should "filter over gt/lt match" in withIndex { index =>
    {
      val days1 = DateField.parseString("2024-01-01").toOption.get
      val days2 = DateField.parseString("2024-03-01").toOption.get
      val results = index.search(filters =
        Some(
          Filters(include =
            Some(
              RangePredicate(
                "date",
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
