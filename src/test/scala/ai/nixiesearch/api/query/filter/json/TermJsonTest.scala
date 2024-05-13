package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Predicate.TermPredicate
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TermJsonTest extends AnyFlatSpec with Matchers {
  it should "encode term predicate" in {
    val result = TermPredicate("color", "red").asJson.noSpaces
    result shouldBe """{"color":"red"}"""
  }

  it should "decode term predicate" in {
    val result = decode[TermPredicate]("""{"color":"red"}""")
    result shouldBe Right(TermPredicate("color", "red"))
  }

  it should "fail on multi field" in {
    val result = decode[TermPredicate]("""{"color":"red", "size":"big"}""")
    result shouldBe a[Left[?, ?]]
  }

  it should "fail on non-string value" in {
    val result = decode[TermPredicate]("""{"color":1}""")
    result shouldBe a[Left[?, ?]]
  }
}
