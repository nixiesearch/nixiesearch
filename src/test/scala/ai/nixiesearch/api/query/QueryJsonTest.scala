package ai.nixiesearch.api.query

import io.circe.parser._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueryJsonTest extends AnyFlatSpec with Matchers {
  it should "dispatch by field name" in {
    val decoded = decode[Query]("""{"multi_match": {"query":"foo","fields":["a","b"]}}""")
    decoded shouldBe Right(MultiMatchQuery("foo", List("a", "b")))
  }

  it should "decode match_all" in {
    val decoded = decode[Query]("""{"match_all": {}}""")
    decoded shouldBe Right(MatchAllQuery())
  }
}
