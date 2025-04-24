package ai.nixiesearch.api.query.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MultiMatchQuery}
import ai.nixiesearch.config.FieldSchema.{
  DateFieldSchema,
  DateTimeFieldSchema,
  IntFieldSchema,
  TextFieldSchema,
  TextListFieldSchema
}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.aggregate.AggregationResult.{TermAggregationResult, TermCount}
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Try
import ai.nixiesearch.config.mapping.FieldName.StringName

class TermAggregationTest extends SearchTest with Matchers {

  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      TextFieldSchema(StringName("title"), search = LexicalSearch()),
      TextFieldSchema(StringName("color"), filter = true, facet = true),
      TextListFieldSchema(StringName("size"), filter = true, facet = true),
      IntFieldSchema(StringName("count"), facet = true),
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
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-01"),
        DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("title", "sleeveless jacket"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-01"),
        DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("title", "short socks"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-02"),
        DateTimeField.applyUnsafe("dt", "2024-01-02T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "4"),
        TextField("title", "winter socks"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-03"),
        DateTimeField.applyUnsafe("dt", "2024-01-03T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "5"),
        TextField("title", "evening dress"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-04"),
        DateTimeField.applyUnsafe("dt", "2024-01-03T00:00:00Z")
      )
    ),
    Document(
      List(
        TextField("_id", "6"),
        TextField("title", "winter socks"),
        TextField("color", "black"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-04"),
        DateTimeField.applyUnsafe("dt", "2024-01-04T00:00:00Z")
      )
    )
  )

  it should "aggregate by color" in withIndex { index =>
    {
      val result = index.searchRaw(aggs = Some(Aggs(Map("color" -> TermAggregation("color", 10)))))
      result.aggs shouldBe Map(
        "color" -> TermAggregationResult(List(TermCount("red", 3), TermCount("white", 2), TermCount("black", 1)))
      )
    }
  }

  it should "aggregate by multi-valued size" in withIndex { index =>
    {
      val result = index.searchRaw(aggs = Some(Aggs(Map("size" -> TermAggregation("size", 10)))))
      result.aggs shouldBe Map(
        "size" -> TermAggregationResult(List(TermCount("2", 6), TermCount("1", 4)))
      )
    }
  }

  it should "aggregate by color when searching" in withIndex { index =>
    {
      val query  = MultiMatchQuery("socks", List("title"))
      val result = index.searchRaw(query = query, aggs = Some(Aggs(Map("color" -> TermAggregation("color", 10)))))
      result.aggs shouldBe Map(
        "color" -> TermAggregationResult(List(TermCount("red", 2), TermCount("black", 1), TermCount("white", 1)))
      )
    }
  }

  it should "aggregate by color when filtering" in withIndex { index =>
    {
      val result = index.searchRaw(
        aggs = Some(Aggs(Map("color" -> TermAggregation("color", 10)))),
        filters = Some(Filters(include = Some(TermPredicate("size", "1"))))
      )
      result.aggs shouldBe Map(
        "color" -> TermAggregationResult(List(TermCount("white", 2), TermCount("black", 1), TermCount("red", 1)))
      )
    }
  }

  it should "select nothing on too narrow query" in withIndex { index =>
    {
      val query  = MultiMatchQuery("nope", List("title"))
      val result = index.searchRaw(query = query, aggs = Some(Aggs(Map("color" -> TermAggregation("color", 10)))))
      result.aggs shouldBe Map("color" -> TermAggregationResult(List()))
    }
  }

  it should "aggregate by int field" in withIndex { index =>
    {
      val result = index.searchRaw(aggs = Some(Aggs(Map("count" -> TermAggregation("count", 10)))))
      result.aggs shouldBe Map("count" -> TermAggregationResult(List(TermCount("1", 6))))

    }
  }

  it should "aggregate over dates" in withIndex { index =>
    {
      val query  = MatchAllQuery()
      val result = index.searchRaw(query = query, aggs = Some(Aggs(Map("date" -> TermAggregation("date", 10)))))
      result.aggs shouldBe Map(
        "date" -> TermAggregationResult(
          List(
            TermCount("2024-01-01", 2),
            TermCount("2024-01-04", 2),
            TermCount("2024-01-02", 1),
            TermCount("2024-01-03", 1)
          )
        )
      )
    }
  }
  it should "aggregate over timestamps" in withIndex { index =>
    {
      val query  = MatchAllQuery()
      val result = index.searchRaw(query = query, aggs = Some(Aggs(Map("dt" -> TermAggregation("dt", 10)))))
      result.aggs shouldBe Map(
        "dt" -> TermAggregationResult(
          List(
            TermCount("2024-01-01T00:00:00Z", 2),
            TermCount("2024-01-03T00:00:00Z", 2),
            TermCount("2024-01-02T00:00:00Z", 1),
            TermCount("2024-01-04T00:00:00Z", 1)
          )
        )
      )
    }
  }

}
