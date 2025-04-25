package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.{BoolQuery, MatchQuery}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class BoolQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode sample query" in {
    val str     = """{"should": [{"match": {"foo": "bar"}}]}"""
    val decoded = decode[BoolQuery](str)
    decoded shouldBe Right(BoolQuery(should = List(MatchQuery("foo", "bar"))))
  }

  it should "fail on no args" in {
    val str     = """{"should": []}"""
    val decoded = decode[BoolQuery](str)
    decoded shouldBe a[Left[?, ?]]
  }
}
