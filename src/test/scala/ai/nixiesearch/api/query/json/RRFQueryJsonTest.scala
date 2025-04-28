package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.rerank.RRFQuery
import ai.nixiesearch.api.query.retrieve.{MatchQuery, SemanticQuery}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class RRFQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode query from quickstart" in {
    val str = """{
                |  "queries": [
                |    {"match": {"title": "matrix"}},
                |    {"semantic": {"title": "guy in black suit fights computers"}}
                |  ]
                |}""".stripMargin
    val decoded = decode[RRFQuery](str)
    decoded shouldBe Right(
      RRFQuery(List(MatchQuery("title", "matrix"), SemanticQuery("title", "guy in black suit fights computers")))
    )
  }
}
