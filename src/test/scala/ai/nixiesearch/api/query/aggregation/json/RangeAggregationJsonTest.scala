package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation
import ai.nixiesearch.api.aggregation.Aggregation.AggRange.RangeFromTo
import ai.nixiesearch.api.aggregation.Aggregation.RangeAggregation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class RangeAggregationJsonTest extends AnyFlatSpec with Matchers {
  it should "decode range aggregation" in {
    val result = decode[Aggregation]("""{"range": {"field":"f", "ranges": [{"from":10, "to":20}]}}""")
    result shouldBe Right(RangeAggregation("f", List(RangeFromTo(10, 20))))
  }
}
