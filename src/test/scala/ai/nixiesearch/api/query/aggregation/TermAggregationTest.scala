package ai.nixiesearch.api.query.aggregation

import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.MultiMatchQuery
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IntField, TextField, TextListField}
import ai.nixiesearch.core.aggregate.AggregationResult.{TermAggregationResult, TermCount}
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class TermAggregationTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema("_id", filter = true),
      TextFieldSchema("title", search = LexicalSearch()),
      TextFieldSchema("color", filter = true, facet = true),
      TextListFieldSchema("size", filter = true, facet = true),
      IntFieldSchema("count", facet = true)
    )
  )
  val docs = List(
    Document(
      List(
        TextField("_id", "1"),
        TextField("title", "long socks"),
        TextField("color", "red"),
        TextListField("size", "1", "2"),
        IntField("count", 1)
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("title", "sleeveless jacket"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1)
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("title", "short socks"),
        TextField("color", "red"),
        TextListField("size", "2"),
        IntField("count", 1)
      )
    ),
    Document(
      List(
        TextField("_id", "4"),
        TextField("title", "winter socks"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1)
      )
    ),
    Document(
      List(
        TextField("_id", "5"),
        TextField("title", "evening dress"),
        TextField("color", "white"),
        TextListField("size", "1", "2"),
        IntField("count", 1)
      )
    ),
    Document(
      List(
        TextField("_id", "6"),
        TextField("title", "winter socks"),
        TextField("color", "black"),
        TextListField("size", "1", "2"),
        IntField("count", 1)
      )
    )
  )

  it should "aggregate by color" in new Index {
    val result = searchRaw(aggs = Aggs(Map("color" -> TermAggregation("color", 10))))
    result.aggs shouldBe Map(
      "color" -> TermAggregationResult(List(TermCount("red", 3), TermCount("white", 2), TermCount("black", 1)))
    )
  }

  it should "aggregate by multi-valued size" in new Index {
    val result = searchRaw(aggs = Aggs(Map("size" -> TermAggregation("size", 10))))
    result.aggs shouldBe Map(
      "size" -> TermAggregationResult(List(TermCount("2", 6), TermCount("1", 4)))
    )
  }

  it should "aggregate by color when searching" in new Index {
    val query  = MultiMatchQuery("socks", List("title"))
    val result = searchRaw(query = query, aggs = Aggs(Map("color" -> TermAggregation("color", 10))))
    result.aggs shouldBe Map(
      "color" -> TermAggregationResult(List(TermCount("red", 2), TermCount("black", 1), TermCount("white", 1)))
    )
  }

  it should "aggregate by color when filtering" in new Index {
    val result = searchRaw(
      aggs = Aggs(Map("color" -> TermAggregation("color", 10))),
      filters = Filters(include = Some(TermPredicate("size", "1")))
    )
    result.aggs shouldBe Map(
      "color" -> TermAggregationResult(List(TermCount("white", 2), TermCount("black", 1), TermCount("red", 1)))
    )
  }

  it should "select nothing on too narrow query" in new Index {
    val query  = MultiMatchQuery("nope", List("title"))
    val result = searchRaw(query = query, aggs = Aggs(Map("color" -> TermAggregation("color", 10))))
    result.aggs shouldBe Map("color" -> TermAggregationResult(List()))
  }

  it should "fail on aggregating by a non-string field" in new Index {
    val result = Try(searchRaw(aggs = Aggs(Map("count" -> TermAggregation("count", 10)))))
    result.isFailure shouldBe true
  }

}
