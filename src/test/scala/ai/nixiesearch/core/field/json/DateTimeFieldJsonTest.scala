package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.DateTimeFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.DateTimeField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class DateTimeFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode ISO-8601 datetime string" in {
    val result = decode(DateTimeFieldSchema(StringName("datetime")), """{"datetime": "1970-01-01T00:00:01Z"}""")
    result shouldBe Some(DateTimeField("datetime", 1000)) // milliseconds since epoch
  }

  it should "decode epoch datetime" in {
    val result = decode(DateTimeFieldSchema(StringName("datetime")), """{"datetime": "1970-01-01T00:00:00Z"}""")
    result shouldBe Some(DateTimeField("datetime", 0))
  }

  it should "decode datetime with timezone" in {
    // 1970-01-01T01:00:00+01:00 is the same as 1970-01-01T00:00:00Z
    val result = decode(DateTimeFieldSchema(StringName("datetime")), """{"datetime": "1970-01-01T01:00:00+01:00"}""")
    result shouldBe Some(DateTimeField("datetime", 0))
  }

  it should "fail on invalid datetime format" in {
    val result = Try(decode(DateTimeFieldSchema(StringName("datetime")), """{"datetime": "01/01/1970 00:00:00"}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-string values" in {
    val result = Try(decode(DateTimeFieldSchema(StringName("datetime")), """{"datetime": 1000}"""))
    result shouldBe a[Failure[?]]
  }
}
