package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Predicate.RangePredicate
import ai.nixiesearch.api.filter.Predicate.RangePredicate.{RangeGte, RangeGteLte}
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RangeJsonTest extends AnyFlatSpec with Matchers {
  it should "decode gte/lte range" in {
    val result = decode[RangePredicate]("""{"price":{"gte":10,"lte":100}}""")
    result shouldBe Right(RangeGteLte("price", 10.0, 100.0))
  }

  it should "decode gte range" in {
    val result = decode[RangePredicate]("""{"price":{"gte":10}}""")
    result shouldBe Right(RangeGte("price", 10.0))
  }

  it should "fail decoding when no gte+lte" in {
    val result = decode[RangePredicate]("""{"price":{"nope":10}}""")
    result shouldBe a[Left[_, RangePredicate]]
  }

  it should "encode gte/lte range" in {
    val result = RangeGteLte("price", 10.0, 100.0).asJson(RangePredicate.rangePredicateEncoder).noSpaces
    result shouldBe """{"price":{"gte":10.0,"lte":100.0}}"""
  }
}
