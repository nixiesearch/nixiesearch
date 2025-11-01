package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.DateFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.DateField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class DateFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode ISO-8601 date string" in {
    val result = decode(DateFieldSchema(StringName("date")), """{"date": "2025-01-01"}""")
    result shouldBe Some(DateField("date", 20089)) // days since 1970-01-01
  }

  it should "decode epoch date" in {
    val result = decode(DateFieldSchema(StringName("date")), """{"date": "1970-01-01"}""")
    result shouldBe Some(DateField("date", 0))
  }

  it should "decode date before epoch" in {
    val result = decode(DateFieldSchema(StringName("date")), """{"date": "1969-12-31"}""")
    result shouldBe Some(DateField("date", -1))
  }

  it should "fail on invalid date format" in {
    val result = Try(decode(DateFieldSchema(StringName("date")), """{"date": "01/01/2025"}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-string values" in {
    val result = Try(decode(DateFieldSchema(StringName("date")), """{"date": 20089}"""))
    result shouldBe a[Failure[?]]
  }
}
