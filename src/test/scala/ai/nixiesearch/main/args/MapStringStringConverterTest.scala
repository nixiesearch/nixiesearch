package ai.nixiesearch.main.args

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MapStringStringConverterTest extends AnyFlatSpec with Matchers {

  it should "convert single pair" in {
    MapStringStringConverter.convert("foo=bar") shouldBe Right(Map("foo" -> "bar"))
  }

  it should "convert multiple pairs" in {
    MapStringStringConverter.convert("foo=bar,a=b") shouldBe Right(Map("foo" -> "bar", "a" -> "b"))
  }

  it should "parse empty string" in {
    MapStringStringConverter.convert("") shouldBe Right(Map.empty)
  }

  it should "parse empty pairs" in {
    MapStringStringConverter.convert("foo=bar,,a=b") shouldBe Right(Map("foo" -> "bar", "a" -> "b"))
  }

  it should "fail on empty key/value" in {
    MapStringStringConverter.convert("foo=") shouldBe a[Left[?, ?]]
    MapStringStringConverter.convert("=foo") shouldBe a[Left[?, ?]]
    MapStringStringConverter.convert("foo") shouldBe a[Left[?, ?]]
  }
}
