package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.core.aggregate.AggregationResult.{TermAggregationResult, TermCount}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

class AggregationEncoderJsonTest extends AnyFlatSpec with Matchers {
  it should "encode response with term aggregation" in {
    val response = SearchResponse(
      took = 1L,
      ts = 1L,
      hits = Nil,
      aggs = Map("a" -> TermAggregationResult(buckets = List(TermCount("foo", 1))))
    )
    import ai.nixiesearch.util.TestIndexMapping.given
    val json = response.asJson.noSpaces
    json shouldBe """{"took":1,"hits":[],"aggs":{"a":{"buckets":[{"term":"foo","count":1}]}},"ts":1}"""
  }
}
