package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.config.mapping.FieldName.StringName
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class SearchRequestJsonTest extends AnyFlatSpec with Matchers {
  it should "decode valid query" in {
    val str = """{
                |  "query": {
                |    "match_all": {}
                |  },
                |  "fields": ["title", "desc"],
                |  "size": 20,
                |  "aggs": {
                |    "color_counts": {"term": {"field": "color"}}
                |  },
                |  "filters": {
                |    "include": {"term": {"category": "pants"}}
                |  }
                |}
                |""".stripMargin
    val decoded = decode[SearchRequest](str)
    decoded shouldBe Right(
      SearchRequest(
        query = MatchAllQuery(),
        fields = List(StringName("title"), StringName("desc")),
        size = 20,
        aggs = Some(Aggs(Map("color_counts" -> TermAggregation("color")))),
        filters = Some(Filters(include = Some(TermPredicate("category", "pants"))))
      )
    )
  }
  it should "decode complain on extra fields" in {
    val str =
      """{
        |  "query": {
        |    "match_all": {}
        |  },
        |  "fields": ["title", "desc"],
        |  "size": 20,
        |  "aggs": {
        |    "color_counts": {"term": {"field": "color"}}
        |  },
        |  "filter": {
        |    "include": {"term": {"category": "pants"}}
        |  }
        |}
        |""".stripMargin
    val decoded = decode[SearchRequest](str)
    decoded shouldBe a[Left[?, ?]]
  }
}
