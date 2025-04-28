package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.{DisMaxQuery, MatchQuery}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class DisMaxQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode sample request" in {
    val str     = """{"queries": [{"match": {"foo":"bar"}},{"match": {"baz":"qux"}}]}"""
    val decoded = decode[DisMaxQuery](str)
    decoded shouldBe Right(DisMaxQuery(List(MatchQuery("foo", "bar"), MatchQuery("baz", "qux"))))
  }

  it should "fail on empty queries" in {
    val str     = """{"queries": []}"""
    val decoded = decode[DisMaxQuery](str)
    decoded shouldBe a[Left[?, ?]]
  }

  it should "fail on one query" in {
    val str     = """{"queries": [{"match": {"foo":"bar"}}]}"""
    val decoded = decode[DisMaxQuery](str)
    decoded shouldBe a[Left[?, ?]]
  }
}
