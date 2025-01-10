package ai.nixiesearch.api.query.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.AggRange
import ai.nixiesearch.api.aggregation.Aggregation.RangeAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.api.query.MultiMatchQuery
import ai.nixiesearch.config.FieldSchema.{
  DateFieldSchema,
  DateTimeFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema
}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.FiniteRange.Higher.{Lt, Lte}
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import ai.nixiesearch.core.aggregate.AggregationResult.{RangeAggregationResult, RangeCount}
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import scala.util.Try
import ai.nixiesearch.config.mapping.FieldName.StringName

class RangeAggregationTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      TextFieldSchema(StringName("title"), search = LexicalSearch()),
      TextFieldSchema(StringName("color"), filter = true, facet = true),
      IntFieldSchema(StringName("count"), facet = true, filter = true),
      FloatFieldSchema(StringName("fcount"), facet = true),
      LongFieldSchema(StringName("lcount"), facet = true),
      DoubleFieldSchema(StringName("dcount"), facet = true),
      DateFieldSchema(StringName("date"), facet = true),
      DateTimeFieldSchema(StringName("dt"), facet = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(
      List(
        TextField("_id", "1"),
        TextField("title", "long socks"),
        TextField("color", "red"),
        IntField("count", 1),
        FloatField("fcount", 1.0f),
        LongField("lcount", 1),
        DoubleField("dcount", 1),
        DateField.applyUnsafe("date", "2024-01-01"),
        DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("title", "sleeveless jacket"),
        TextField("color", "red"),
        IntField("count", 2),
        FloatField("fcount", 2.0f),
        LongField("lcount", 2),
        DoubleField("dcount", 2),
        DateField.applyUnsafe("date", "2024-01-02"),
        DateTimeField.applyUnsafe("dt", "2024-01-02T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("title", "short socks"),
        TextField("color", "red"),
        IntField("count", 3),
        FloatField("fcount", 3.0f),
        LongField("lcount", 3),
        DoubleField("dcount", 3),
        DateField.applyUnsafe("date", "2024-01-03"),
        DateTimeField.applyUnsafe("dt", "2024-01-03T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "4"),
        TextField("title", "winter socks"),
        TextField("color", "white"),
        IntField("count", 4),
        FloatField("fcount", 4.0f),
        LongField("lcount", 4),
        DoubleField("dcount", 4),
        DateField.applyUnsafe("date", "2024-01-04"),
        DateTimeField.applyUnsafe("dt", "2024-01-04T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "5"),
        TextField("title", "evening dress"),
        TextField("color", "white"),
        IntField("count", 5),
        FloatField("fcount", 5.0f),
        LongField("lcount", 5),
        DoubleField("dcount", 5),
        DateField.applyUnsafe("date", "2024-01-05"),
        DateTimeField.applyUnsafe("dt", "2024-01-05T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "6"),
        TextField("title", "winter socks"),
        TextField("color", "black"),
        IntField("count", 6),
        FloatField("fcount", 6.0f),
        LongField("lcount", 6),
        DoubleField("dcount", 6),
        DateField.applyUnsafe("date", "2024-01-06"),
        DateTimeField.applyUnsafe("dt", "2024-01-06T00:00:00Z")
      )
    )
  )

  it should "aggregate over int range with gte-lt" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "count" -> RangeAggregation("count", List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4))))
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "count" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 2),
            RangeCount(Some(Gte(4)), None, 3)
          )
        )
      )
    }
  }

  it should "aggregate over int range with gt-lte" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "count" -> RangeAggregation("count", List(AggRange(Lte(2)), AggRange(Gt(2), Lte(4)), AggRange(Gt(4))))
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "count" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lte(2)), 2),
            RangeCount(Some(Gt(2)), Some(Lte(4.0)), 2),
            RangeCount(Some(Gt(4)), None, 2)
          )
        )
      )
    }
  }

  it should "aggregate over float range" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "fcount" -> RangeAggregation(
                "fcount",
                List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "fcount" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 2),
            RangeCount(Some(Gte(4)), None, 3)
          )
        )
      )
    }
  }

  it should "aggregate over long range" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "lcount" -> RangeAggregation(
                "lcount",
                List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "lcount" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 2),
            RangeCount(Some(Gte(4)), None, 3)
          )
        )
      )
    }
  }

  it should "aggregate over double range" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "dcount" -> RangeAggregation(
                "dcount",
                List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "dcount" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 2),
            RangeCount(Some(Gte(4)), None, 3)
          )
        )
      )
    }
  }

  it should "aggregate and filter" in withIndex { index =>
    {
      val result = index.searchRaw(
        aggs = Some(
          Aggs(
            Map(
              "count" -> RangeAggregation("count", List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4))))
            )
          )
        ),
        filters = Some(Filters(include = Some(RangePredicate("count", None, Some(Lte(2.5))))))
      )
      result.aggs shouldBe Map(
        "count" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 1),
            RangeCount(Some(Gte(4)), None, 0)
          )
        )
      )
    }
  }

  it should "fail when aggregating over text field" in withIndex { index =>
    {
      val result = Try(
        index.searchRaw(aggs =
          Some(
            Aggs(
              Map(
                "count" -> RangeAggregation(
                  "title",
                  List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4)))
                )
              )
            )
          )
        )
      )
      result.isFailure shouldBe true
    }
  }

  it should "return zeroes for out of range facets" in withIndex { index =>
    {
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "count" -> RangeAggregation(
                "count",
                List(AggRange(Lt(20)), AggRange(Gte(20), Lt(40)), AggRange(Gte(40)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "count" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(20)), 6),
            RangeCount(Some(Gte(20)), Some(Lt(40)), 0),
            RangeCount(Some(Gte(40)), None, 0)
          )
        )
      )
    }
  }
  it should "aggregate over int range and search" in withIndex { index =>
    {
      val result = index.searchRaw(
        aggs = Some(
          Aggs(
            Map(
              "count" -> RangeAggregation("count", List(AggRange(Lt(2)), AggRange(Gte(2), Lt(4)), AggRange(Gte(4))))
            )
          )
        ),
        query = MultiMatchQuery("socks", List("title"))
      )
      result.aggs shouldBe Map(
        "count" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(2)), 1),
            RangeCount(Some(Gte(2)), Some(Lt(4)), 1),
            RangeCount(Some(Gte(4)), None, 2)
          )
        )
      )
    }
  }

  it should "aggregate over dates" in withIndex { index =>
    {
      val day1 = DateField.parseString("2024-01-02").toOption.get
      val day2 = DateField.parseString("2024-01-04").toOption.get
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "date" -> RangeAggregation(
                "date",
                List(AggRange(Lt(day1)), AggRange(Gte(day1), Lt(day2)), AggRange(Gte(day2)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "date" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(day1)), 1),
            RangeCount(Some(Gte(day1)), Some(Lt(day2)), 2),
            RangeCount(Some(Gte(day2)), None, 3)
          )
        )
      )
    }
  }

  it should "aggregate over datetimes" in withIndex { index =>
    {
      val day1 = DateTimeField.parseString("2024-01-02T00:00:00Z").toOption.get
      val day2 = DateTimeField.parseString("2024-01-04T00:00:00Z").toOption.get
      val result = index.searchRaw(aggs =
        Some(
          Aggs(
            Map(
              "dt" -> RangeAggregation(
                "dt",
                List(AggRange(Lt(day1)), AggRange(Gte(day1), Lt(day2)), AggRange(Gte(day2)))
              )
            )
          )
        )
      )
      result.aggs shouldBe Map(
        "dt" -> RangeAggregationResult(
          List(
            RangeCount(None, Some(Lt(day1)), 1),
            RangeCount(Some(Gte(day1)), Some(Lt(day2)), 2),
            RangeCount(Some(Gte(day2)), None, 3)
          )
        )
      )
    }
  }

}
