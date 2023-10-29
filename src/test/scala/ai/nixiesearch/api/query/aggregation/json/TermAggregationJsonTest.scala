package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation
import ai.nixiesearch.api.aggregation.Aggregation.TermAggSize.AllTermAggSize
import ai.nixiesearch.api.aggregation.Aggregation.TermAggregation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class TermAggregationJsonTest extends AnyFlatSpec with Matchers {
  it should "decode term aggregation" in {
    val result = decode[Aggregation]("""{"term": {"field": "f", "size": 5}}""")
    result shouldBe Right(TermAggregation("f", 5))
  }

  it should "decode all agg size" in {
    val result = decode[Aggregation]("""{"term": {"field": "f", "size": "all"}}""")
    result shouldBe Right(TermAggregation("f", AllTermAggSize))
  }

  it should "encode all agg size" in {
    val json: Aggregation = TermAggregation("f", AllTermAggSize)
    json.asJson.noSpaces shouldBe """{"term":{"field":"f","size":"all"}}"""
  }
}
