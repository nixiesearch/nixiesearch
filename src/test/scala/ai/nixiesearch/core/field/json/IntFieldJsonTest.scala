package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.IntField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class IntFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode int values" in {
    val result = decode(IntFieldSchema(StringName("count")), """{"count": 42}""")
    result shouldBe Some(IntField("count", 42))
  }

  it should "decode negative values" in {
    val result = decode(IntFieldSchema(StringName("count")), """{"count": -42}""")
    result shouldBe Some(IntField("count", -42))
  }

  it should "fail on null values" in {
    val result = Try(decode(IntFieldSchema(StringName("count")), """{"count": null}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-int values" in {
    val result = Try(decode(IntFieldSchema(StringName("count")), """{"count": "42"}"""))
    result shouldBe a[Failure[?]]
  }
}
