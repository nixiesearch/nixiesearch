package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation
import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class TermAggregationJsonTest extends AnyFlatSpec with Matchers {
  it should "decode term aggregation" in {
    val result = decode[Aggregation]("""{"term": {"field": "f", "size": 5}}""")
    result shouldBe Right(TermAggregation("f", 5))
  }
}
