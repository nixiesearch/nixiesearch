package ai.nixiesearch.api.query.aggregation.json

import ai.nixiesearch.api.aggregation.Aggregation.AggRange
import ai.nixiesearch.core.FiniteRange.Higher.Lt
import ai.nixiesearch.core.FiniteRange.Lower.{Gt, Gte}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class RangeJsonTest extends AnyFlatSpec with Matchers {
  it should "decode gte-lt range" in {
    val result = decode[AggRange]("""{"gte":1,"lt":2}""")
    result shouldBe Right(AggRange(Gte(1), Lt(2)))
  }

  it should "decode gt-lt range" in {
    val result = decode[AggRange]("""{"gt":1,"lt":2}""")
    result shouldBe Right(AggRange(Gt(1), Lt(2)))
  }

  it should "fail on empty range" in {
    val result = decode[AggRange]("""{"ffrom":1,"tto":2}""")
    result shouldBe a[Left[?, ?]]
  }

}
