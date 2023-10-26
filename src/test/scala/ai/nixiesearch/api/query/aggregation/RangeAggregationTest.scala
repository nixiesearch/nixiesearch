package ai.nixiesearch.api.query.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.AggRange.{RangeFrom, RangeFromTo, RangeTo}
import ai.nixiesearch.api.aggregation.Aggregation.RangeAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.api.filter.Predicate.RangePredicate.RangeLt
import ai.nixiesearch.api.query.MultiMatchQuery
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, IntFieldSchema, TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import ai.nixiesearch.core.FiniteRange.Higher.{Lt, Lte}
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import ai.nixiesearch.core.aggregate.AggregationResult.{RangeAggregationResult, RangeCount}
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RangeAggregationTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("_id", filter = true),
      TextFieldSchema("title", search = LexicalSearch()),
      TextFieldSchema("color", filter = true, facet = true),
      IntFieldSchema("count", facet = true, filter = true),
      FloatFieldSchema("fcount", facet = true)
    )
  )
  val docs = List(
    Document(
      List(
        TextField("_id", "1"),
        TextField("title", "long socks"),
        TextField("color", "red"),
        IntField("count", 1),
        FloatField("fcount", 1.0f)
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("title", "sleeveless jacket"),
        TextField("color", "red"),
        IntField("count", 2),
        FloatField("fcount", 2.0f)
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("title", "short socks"),
        TextField("color", "red"),
        IntField("count", 3),
        FloatField("fcount", 3.0f)
      )
    ),
    Document(
      List(
        TextField("_id", "4"),
        TextField("title", "winter socks"),
        TextField("color", "white"),
        IntField("count", 4),
        FloatField("fcount", 4.0f)
      )
    ),
    Document(
      List(
        TextField("_id", "5"),
        TextField("title", "evening dress"),
        TextField("color", "white"),
        IntField("count", 5),
        FloatField("fcount", 5.0f)
      )
    ),
    Document(
      List(
        TextField("_id", "6"),
        TextField("title", "winter socks"),
        TextField("color", "black"),
        IntField("count", 6),
        FloatField("fcount", 6.0f)
      )
    )
  )

  it should "aggregate over int range with gte-lt" in new Index {
    val result = searchRaw(aggs =
      Aggs(
        Map("count" -> RangeAggregation("count", List(RangeTo(Lt(2)), RangeFromTo(Gte(2), Lt(4)), RangeFrom(Gte(4)))))
      )
    )
    result.aggs shouldBe Map(
      "count" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lt(2.0)), 1),
          RangeCount(Some(Gte(2.0)), Some(Lt(4.0)), 2),
          RangeCount(Some(Gte(4.0)), None, 3)
        )
      )
    )
  }

  it should "aggregate over int range with gt-lte" in new Index {
    val result = searchRaw(aggs =
      Aggs(
        Map("count" -> RangeAggregation("count", List(RangeTo(Lte(2)), RangeFromTo(Gt(2), Lte(4)), RangeFrom(Gt(4)))))
      )
    )
    result.aggs shouldBe Map(
      "count" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lte(2.0)), 2),
          RangeCount(Some(Gt(2.0)), Some(Lte(4.0)), 2),
          RangeCount(Some(Gt(4.0)), None, 2)
        )
      )
    )
  }

  it should "aggregate over float range" in new Index {
    val result = searchRaw(aggs =
      Aggs(
        Map("fcount" -> RangeAggregation("fcount", List(RangeTo(Lt(2)), RangeFromTo(Gte(2), Lt(4)), RangeFrom(Gte(4)))))
      )
    )
    result.aggs shouldBe Map(
      "fcount" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lt(2.0)), 1),
          RangeCount(Some(Gte(2.0)), Some(Lt(4.0)), 2),
          RangeCount(Some(Gte(4.0)), None, 3)
        )
      )
    )
  }

  it should "aggregate and filter" in new Index {
    val result = searchRaw(
      aggs = Aggs(
        Map("count" -> RangeAggregation("count", List(RangeTo(Lt(2)), RangeFromTo(Gte(2), Lt(4)), RangeFrom(Gte(4)))))
      ),
      filters = Filters(include = Some(RangeLt("count", Lte(2.5))))
    )
    result.aggs shouldBe Map(
      "count" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lt(2.0)), 1),
          RangeCount(Some(Gte(2.0)), Some(Lt(4.0)), 2),
          RangeCount(Some(Gte(4.0)), None, 0)
        )
      )
    )
  }

  it should "fail when aggregating over text field" in new Index {
    val result = Try(
      searchRaw(aggs =
        Aggs(
          Map("count" -> RangeAggregation("title", List(RangeTo(Lt(2)), RangeFromTo(Gte(2), Lt(4)), RangeFrom(Gte(4)))))
        )
      )
    )
    result.isFailure shouldBe true
  }

  it should "return zeroes for out of range facets" in new Index {
    val result = searchRaw(aggs =
      Aggs(
        Map(
          "count" -> RangeAggregation("count", List(RangeTo(Lt(20)), RangeFromTo(Gte(20), Lt(40)), RangeFrom(Gte(40))))
        )
      )
    )
    result.aggs shouldBe Map(
      "count" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lt(20.0)), 6),
          RangeCount(Some(Gte(20.0)), Some(Lt(40.0)), 0),
          RangeCount(Some(Gte(40.0)), None, 0)
        )
      )
    )
  }
  it should "aggregate over int range and search" in new Index {
    val result = searchRaw(
      aggs = Aggs(
        Map("count" -> RangeAggregation("count", List(RangeTo(Lt(2)), RangeFromTo(Gte(2), Lt(4)), RangeFrom(Gte(4)))))
      ),
      query = MultiMatchQuery("socks", List("title"))
    )
    result.aggs shouldBe Map(
      "count" -> RangeAggregationResult(
        List(
          RangeCount(None, Some(Lt(2.0)), 1),
          RangeCount(Some(Gte(2.0)), Some(Lt(4.0)), 1),
          RangeCount(Some(Gte(4.0)), None, 2)
        )
      )
    )
  }

}
