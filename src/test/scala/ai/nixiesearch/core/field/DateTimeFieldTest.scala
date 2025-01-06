package ai.nixiesearch.core.field

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DateTimeFieldTest extends AnyFlatSpec with Matchers {
  it should "parse ISO date time with no zone" in {
    DateTimeField.parseString("1970-01-01T00:00:01Z") shouldBe Right(1000L)
  }

  it should "parse ISO date time with zone" in {
    DateTimeField.parseString("1970-01-01T00:00:01-01:00") shouldBe Right(3600 * 1000 + 1000L)
  }

  it should "convert it back to string" in {
    DateTimeField.writeString(1000) shouldBe "1970-01-01T00:00:01Z"
  }
}
