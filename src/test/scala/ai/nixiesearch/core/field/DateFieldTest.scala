package ai.nixiesearch.core.field

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DateFieldTest extends AnyFlatSpec with Matchers {
  it should "parse date" in {
    val days = DateField.parseString("1970-01-10")
    days shouldBe Right(9)
  }

  it should "fail on wrong date" in {
    val days = DateField.parseString("1970-01-99")
    days shouldBe a[Left[?, ?]]
  }

  it should "convert date to string" in {
    DateField.writeString(9) shouldBe "1970-01-10"
  }
}
