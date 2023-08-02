package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.MatchQuery
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MatchQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode simple layout" in {
    val result = decode[MatchQuery]("""{"field":"query"}""")
    result shouldBe Right(MatchQuery("field", "query"))
  }

  it should "decode nested layout" in {
    val result = decode[MatchQuery]("""{"field":{"query":"foo"}}""")
    result shouldBe Right(MatchQuery("field", "foo"))
  }
}
