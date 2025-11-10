package ai.nixiesearch.util

import ai.nixiesearch.util.SizeTest.SizeWrap
import io.circe.Codec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto.*
import io.circe.parser.*

class SizeTest extends AnyFlatSpec with Matchers {
  it should "parse raw int" in {
    decode[SizeWrap]("""{"value": "123"}""") shouldBe Right(SizeWrap(Size(123, "123")))
  }

  it should "parse exponents" in {

    decode[SizeWrap]("""{"value": "9.2E18 mb"}""") shouldBe Right(SizeWrap(Size(9223372036854775807L, "9.2E18 mb")))
    decode[SizeWrap]("""{"value": "9.2e18 mb"}""") shouldBe Right(SizeWrap(Size(9223372036854775807L, "9.2e18 mb")))
  }

  it should "parse space separated gigs" in {
    decode[SizeWrap]("""{"value": "123 GB"}""") shouldBe Right(SizeWrap(Size(123 * 1024 * 1024 * 1024L, "123 GB")))
  }

  it should "parse no-space kbs" in {
    decode[SizeWrap]("""{"value": "123kb"}""") shouldBe Right(SizeWrap(Size(123 * 1024L, "123kb")))
  }

  it should "parse no-space ks" in {
    decode[SizeWrap]("""{"value": "123k"}""") shouldBe Right(SizeWrap(Size(123 * 1024L, "123k")))
  }

  it should "parse no-space kbs with dots" in {
    decode[SizeWrap]("""{"value": "0.5kb"}""") shouldBe Right(SizeWrap(Size(math.round(0.5 * 1024L), "0.5kb")))
  }

}

object SizeTest {
  case class SizeWrap(value: Size)
  given sizeCodec: Codec[SizeWrap] = deriveCodec
}
