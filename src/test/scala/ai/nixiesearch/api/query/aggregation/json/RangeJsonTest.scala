package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation.AggRange.RangeFromTo
import ai.nixiesearch.api.aggregation.Aggregation.{AggRange, RangeAggregation}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class RangeJsonTest extends AnyFlatSpec with Matchers {
  it should "decode from-to range" in {
    val result = decode[AggRange]("""{"from":1,"to":2}""")
    result shouldBe Right(RangeFromTo(1, 2))
  }

  it should "fail on empty range" in {
    val result = decode[AggRange]("""{"ffrom":1,"tto":2}""")
    result shouldBe a[Left[_, _]]
  }

}
