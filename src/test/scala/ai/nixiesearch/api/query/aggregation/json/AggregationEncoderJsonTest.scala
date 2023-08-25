package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import ai.nixiesearch.core.aggregator.AggregationResult.{TermAggregationResult, TermCount}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

class AggregationEncoderJsonTest extends AnyFlatSpec with Matchers {
  it should "encode response with term aggregation" in {
    val response = SearchResponse(
      took = 1L,
      hits = Nil,
      aggs = Map("a" -> TermAggregationResult(buckets = List(TermCount("foo", 1))))
    )
    val json = response.asJson.noSpaces
    json shouldBe """{"took":1,"hits":[],"aggs":{"a":{"buckets":[{"term":"foo","count":1}]}}}"""
  }
}
