package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.LongListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.LongListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class LongListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode long array" in {
    val result =
      decode(LongListFieldSchema(StringName("timestamps")), """{"timestamps": [1234567890123, 9876543210987]}""")
    result shouldBe Some(LongListField("timestamps", List(1234567890123L, 9876543210987L)))
  }

  it should "decode negative values" in {
    val result =
      decode(LongListFieldSchema(StringName("timestamps")), """{"timestamps": [-1234567890123, 0, 1234567890123]}""")
    result shouldBe Some(LongListField("timestamps", List(-1234567890123L, 0L, 1234567890123L)))
  }

  it should "fail on non-long arrays" in {
    val result = Try(
      decode(LongListFieldSchema(StringName("timestamps")), """{"timestamps": ["1234567890123", "9876543210987"]}""")
    )
    result shouldBe a[Failure[?]]
  }

  it should "fail on null in array" in {
    val result = Try(
      decode(LongListFieldSchema(StringName("timestamps")), """{"timestamps": [1234567890123, null, 9876543210987]}""")
    )
    result shouldBe a[Failure[?]]
  }
}
