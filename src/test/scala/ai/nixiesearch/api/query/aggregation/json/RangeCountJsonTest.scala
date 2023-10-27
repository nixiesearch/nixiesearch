package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.core.FiniteRange.Higher.Lte
import ai.nixiesearch.core.FiniteRange.Lower.Gte
import ai.nixiesearch.core.aggregate.AggregationResult.RangeCount
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

class RangeCountJsonTest extends AnyFlatSpec with Matchers {
  it should "encode gte-lte" in {
    val rc = RangeCount(Some(Gte(1)), Some(Lte(2)), 10)
    rc.asJson.noSpaces shouldBe """{"gte":1.0,"lte":2.0,"count":10}"""
  }

  it should "encode open gte range" in {
    val rc = RangeCount(Some(Gte(1)), None, 10)
    rc.asJson.noSpaces shouldBe """{"gte":1.0,"count":10}"""
  }
}
