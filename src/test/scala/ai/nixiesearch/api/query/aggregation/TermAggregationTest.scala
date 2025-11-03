package ai.nixiesearch.api.query.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MatchQuery, MultiMatchQuery}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.aggregate.AggregationResult.{TermAggregationResult, TermCount}
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams

class TermAggregationTest extends SearchTest with Matchers {

  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      IdFieldSchema(StringName("_id")),
      TextFieldSchema(StringName("title"), search = SearchParams(lexical = Some(LexicalParams()))),
      TextFieldSchema(StringName("color"), filter = true, facet = true),
      TextListFieldSchema(StringName("size"), filter = true, facet = true),
      IntFieldSchema(StringName("count"), facet = true),
      DateFieldSchema(StringName("date"), facet = true),
      DateTimeFieldSchema(StringName("dt"), facet = true),
      IntListFieldSchema(StringName("intlist"), facet = true),
      LongListFieldSchema(StringName("longlist"), facet = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(
      List(
        IdField("_id", "1"),
        TextField("title", "long socks"),
        TextField("color", "red"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-01"),
        DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z"),
        IntListField("intlist", List(1, 2)),
        LongListField("longlist", List(1, 2))
      )
    ),
    Document(
      List(
        IdField("_id", "2"),
        TextField("title", "sleeveless jacket"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-01"),
        DateTimeField.applyUnsafe("dt", "2024-01-01T00:00:00Z"),
        IntListField("intlist", List(1, 3)),
        LongListField("longlist", List(1, 3))
      )
    ),
    Document(
      List(
        IdField("_id", "3"),
        TextField("title", "short socks"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-02"),
        DateTimeField.applyUnsafe("dt", "2024-01-02T00:00:00Z"),
        IntListField("intlist", List(1, 4)),
        LongListField("longlist", List(1, 4))
      )
    ),
    Document(
      List(
        IdField("_id", "4"),
        TextField("title", "winter socks"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-03"),
        DateTimeField.applyUnsafe("dt", "2024-01-03T00:00:00Z"),
        IntListField("intlist", List(2, 5)),
        LongListField("longlist", List(2, 5))
      )
    ),
    Document(
      List(
        IdField("_id", "5"),
        TextField("title", "evening dress"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-04"),
        DateTimeField.applyUnsafe("dt", "2024-01-03T00:00:00Z"),
        IntListField("intlist", List(3, 6)),
        LongListField("longlist", List(3, 6))
      )
    ),
    Document(
      List(
        IdField("_id", "6"),
        TextField("title", "winter socks"),
        TextField("color", "black"),
        TextListField("size", "1", "2"),
        IntField("count", 1),
        DateField.applyUnsafe("date", "2024-01-04"),
        DateTimeField.applyUnsafe("dt", "2024-01-04T00:00:00Z"),
        IntListField("intlist", List(7)),
        LongListField("longlist", List(7))
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
      val query  = MatchQuery("title", "socks")
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
      val query  = MatchQuery("title", "nope")
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

  it should "aggregate by int[] field" in withIndex { index =>
    {
      val result = index.searchRaw(aggs = Some(Aggs(Map("intlist" -> TermAggregation("intlist", 10)))))
      result.aggs shouldBe Map(
        "intlist" -> TermAggregationResult(
          List(
            TermCount("1", 3),
            TermCount("2", 2),
            TermCount("3", 2),
            TermCount("4", 1),
            TermCount("5", 1),
            TermCount("6", 1),
            TermCount("7", 1)
          )
        )
      )
    }
  }
  it should "aggregate by long[] field" in withIndex { index =>
    {
      val result = index.searchRaw(aggs = Some(Aggs(Map("longlist" -> TermAggregation("longlist", 10)))))
      result.aggs shouldBe Map(
        "longlist" -> TermAggregationResult(
          List(
            TermCount("1", 3),
            TermCount("2", 2),
            TermCount("3", 2),
            TermCount("4", 1),
            TermCount("5", 1),
            TermCount("6", 1),
            TermCount("7", 1)
          )
        )
      )
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
