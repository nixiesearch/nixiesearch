package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Predicate.TermPredicate
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TermJsonTest extends AnyFlatSpec with Matchers {
  it should "encode string term predicate" in {
    val result = TermPredicate("color", "red").asJson.noSpaces
    result shouldBe """{"color":"red"}"""
  }

  it should "decode string term predicate" in {
    val result = decode[TermPredicate]("""{"color":"red"}""")
    result shouldBe Right(TermPredicate("color", "red"))
  }

  it should "encode num term predicate" in {
    val result = TermPredicate("color", 1).asJson.noSpaces
    result shouldBe """{"color":1}"""
  }

  it should "decode bool term predicate" in {
    val result = decode[TermPredicate]("""{"color":true}""")
    result shouldBe Right(TermPredicate("color", true))
  }

  it should "encode bool term predicate" in {
    val result = TermPredicate("color", true).asJson.noSpaces
    result shouldBe """{"color":true}"""
  }

  it should "decode num term predicate" in {
    val result = decode[TermPredicate]("""{"color":1}""")
    result shouldBe Right(TermPredicate("color", 1))
  }

  it should "fail on multi field" in {
    val result = decode[TermPredicate]("""{"color":"red", "size":"big"}""")
    result shouldBe a[Left[?, ?]]
  }

}
