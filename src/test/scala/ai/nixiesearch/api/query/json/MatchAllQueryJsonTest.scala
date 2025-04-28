package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MultiMatchQuery}
import ai.nixiesearch.api.query.Query
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MatchAllQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode match_all" in {
    val decoded = decode[Query]("""{"match_all": {}}""")
    decoded shouldBe Right(MatchAllQuery())
  }
}
