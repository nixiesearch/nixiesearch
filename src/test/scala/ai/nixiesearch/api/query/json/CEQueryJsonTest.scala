package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.rerank.{CEQuery, RRFQuery}
import ai.nixiesearch.api.query.retrieve.{MatchQuery, SemanticQuery}
import ai.nixiesearch.core.nn.ModelRef
import io.circe.parser.decode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CEQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "parse jinja vars" in {
    val result = CEQuery.extractFields("""aaa {{ name }} {{ value }} foo""")
    result shouldBe List("name", "value")
  }

  it should "decode simple ce query" in {
    val str =
      """{
        |  "model": "ce",
        |  "doc_template": "{{ title }}",
        |  "query": "matrix",
        |  "retrieve": {
        |    "semantic": {"title": "guy in black suit fights computers"}
        |  }
        |}""".stripMargin
    val decoded = decode[CEQuery](str)
    decoded shouldBe Right(
      CEQuery(
        model = ModelRef("ce"),
        docTemplate = "{{ title }}",
        query = "matrix",
        retrieve = SemanticQuery("title", "guy in black suit fights computers"),
        fields = Set("title")
      )
    )
  }
}
