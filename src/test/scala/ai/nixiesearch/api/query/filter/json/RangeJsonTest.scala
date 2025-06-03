package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.core.FiniteRange.Higher.*
import ai.nixiesearch.core.FiniteRange.Lower.*
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RangeJsonTest extends AnyFlatSpec with Matchers {
  it should "decode gte/lte range" in {
    val result = decode[RangePredicate]("""{"price":{"gte":10,"lte":100}}""")
    result shouldBe Right(RangePredicate("price", Gte(10.0), Lte(100.0)))
  }

  it should "decode gt/lt range" in {
    val result = decode[RangePredicate]("""{"price":{"gt":10,"lt":100}}""")
    result shouldBe Right(RangePredicate("price", Gt(10.0), Lt(100.0)))
  }

  it should "decode gte range" in {
    val result = decode[RangePredicate]("""{"price":{"gte":10}}""")
    result shouldBe Right(RangePredicate("price", Gte(10.0)))
  }

  it should "fail decoding when no gte+lte" in {
    val result = decode[RangePredicate]("""{"price":{"nope":10}}""")
    result shouldBe a[Left[?, RangePredicate]]
  }

  it should "fail when decoding both gt and gte" in {
    val result = decode[RangePredicate]("""{"price":{"gt":10, "gte":10}}""")
    result shouldBe a[Left[?, RangePredicate]]
  }

  it should "encode gte/lte range" in {
    val result =
      RangePredicate("price", Gte(10.0), Lte(100.0)).asJson(using RangePredicate.rangePredicateEncoder).noSpaces
    result shouldBe """{"price":{"gte":10.0,"lte":100.0}}"""
  }
}
