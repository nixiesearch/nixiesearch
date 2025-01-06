package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation
import ai.nixiesearch.api.aggregation.Aggregation.AggRange
import ai.nixiesearch.api.aggregation.Aggregation.RangeAggregation
import ai.nixiesearch.core.FiniteRange.Higher.Lt
import ai.nixiesearch.core.FiniteRange.Lower.Gte
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class RangeAggregationJsonTest extends AnyFlatSpec with Matchers {
  it should "decode range aggregation" in {
    val result = decode[Aggregation]("""{"range": {"field":"f", "ranges": [{"gte":10, "lt":20}]}}""")
    result shouldBe Right(RangeAggregation("f", List(AggRange(Gte(10), Lt(20)))))
  }
}
