package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.LongField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class LongFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode long values" in {
    val result = decode(LongFieldSchema(StringName("timestamp")), """{"timestamp": 1234567890123}""")
    result shouldBe Some(LongField("timestamp", 1234567890123L))
  }

  it should "decode negative values" in {
    val result = decode(LongFieldSchema(StringName("timestamp")), """{"timestamp": -1234567890123}""")
    result shouldBe Some(LongField("timestamp", -1234567890123L))
  }

  it should "fail on null values" in {
    val result = Try(decode(LongFieldSchema(StringName("timestamp")), """{"timestamp": null}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-long values" in {
    val result = Try(decode(LongFieldSchema(StringName("timestamp")), """{"timestamp": "1234567890123"}"""))
    result shouldBe a[Failure[?]]
  }
}
