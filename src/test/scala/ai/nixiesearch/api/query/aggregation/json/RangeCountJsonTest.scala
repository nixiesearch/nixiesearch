package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.core.FiniteRange.Higher.Lte
import ai.nixiesearch.core.FiniteRange.Lower.Gte
import ai.nixiesearch.core.aggregate.AggregationResult.{RangeAggregationResult, RangeCount}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

class RangeCountJsonTest extends AnyFlatSpec with Matchers {
  it should "encode gte-lte for ints" in {
    val rc = RangeCount(Some(Gte(1)), Some(Lte(2)), 10)
    rc.asJson.noSpaces shouldBe """{"gte":1,"lte":2,"count":10}"""
  }

  it should "encode gte-lte for floats" in {
    val rc = RangeCount(Some(Gte(1.1)), Some(Lte(2.1)), 10)
    rc.asJson.noSpaces shouldBe """{"gte":1.1,"lte":2.1,"count":10}"""
  }

  it should "encode open gte range" in {
    val rc = RangeCount(Some(Gte(1)), None, 10)
    rc.asJson.noSpaces shouldBe """{"gte":1,"count":10}"""
  }

  it should "encode complete response" in {
    val agg = RangeAggregationResult(List(RangeCount(Some(Gte(1)), Some(Lte(2)), 10)))
    agg.asJson.noSpaces shouldBe """{"buckets":[{"gte":1,"lte":2,"count":10}]}"""
  }
}
