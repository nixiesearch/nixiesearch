package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.SemanticQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class SemanticQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode compact version" in {
    val str    = """{"foo": "bar"}"""
    val parsed = decode[SemanticQuery](str)
    parsed shouldBe Right(SemanticQuery("foo", "bar"))
  }

  it should "decode full format" in {
    val str    = """{"field": "foo", "query": "bar"}"""
    val parsed = decode[SemanticQuery](str)
    parsed shouldBe Right(SemanticQuery("foo", "bar"))
  }
}
