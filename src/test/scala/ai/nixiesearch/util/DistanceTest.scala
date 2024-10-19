package ai.nixiesearch.util

import ai.nixiesearch.util.Distance.DistanceUnit.{LightYear, Meter}
import ai.nixiesearch.util.DistanceTest.DistanceWrapper
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.generic.semiauto.*

class DistanceTest extends AnyFlatSpec with Matchers {
  it should "parse with space" in {
    parse("1 m") shouldBe Right(Distance(1.0, Meter))
  }

  it should "parse without space separator" in {
    parse("1m") shouldBe Right(Distance(1.0, Meter))
  }

  it should "parse exp with space" in {
    parse("1e3 m") shouldBe Right(Distance(1000.0, Meter))
  }

  it should "parse exp without space" in {
    parse("1e3m") shouldBe Right(Distance(1000.0, Meter))
  }

  it should "parse units with space" in {
    parse("7 light years") shouldBe Right(Distance(7, LightYear))
  }

  it should "fail on negatives" in {
    parse("-1m") shouldBe a[Left[?, ?]]
  }
  def parse(value: String) = {
    decode[DistanceWrapper](s"""{"value":"$value"}""").map(_.value)
  }
}

object DistanceTest {
  case class DistanceWrapper(value: Distance)
  given distanceDecoder: Decoder[DistanceWrapper] = deriveDecoder
}
