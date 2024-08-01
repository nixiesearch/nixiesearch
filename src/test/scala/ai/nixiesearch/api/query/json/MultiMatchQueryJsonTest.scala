package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MultiMatchQuery
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MultiMatchQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode multi-match" in {
    val decoded = decode[MultiMatchQuery]("""{"query":"foo","fields":["a","b"]}""")
    decoded shouldBe Right(MultiMatchQuery("foo", List("a", "b")))
  }

  it should "decode multi-match with operator" in {
    val decoded = decode[MultiMatchQuery]("""{"query":"foo","fields":["a","b"],"operator":"and"}""")
    decoded shouldBe Right(MultiMatchQuery("foo", List("a", "b"), Operator.AND))
  }
}
