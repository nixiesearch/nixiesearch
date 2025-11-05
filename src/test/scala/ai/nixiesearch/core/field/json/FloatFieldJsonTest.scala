package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.FloatField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class FloatFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode float values" in {
    val result = decode(FloatFieldSchema(StringName("score")), """{"score": 19.99}""")
    result shouldBe Some(FloatField("score", 19.99f))
  }

  it should "decode integer as float" in {
    val result = decode(FloatFieldSchema(StringName("score")), """{"score": 20}""")
    result shouldBe Some(FloatField("score", 20.0f))
  }

  it should "decode negative values" in {
    val result = decode(FloatFieldSchema(StringName("score")), """{"score": -19.99}""")
    result shouldBe Some(FloatField("score", -19.99f))
  }

  it should "fail on null values" in {
    val result = Try(decode(FloatFieldSchema(StringName("score")), """{"score": null}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-numeric values" in {
    val result = Try(decode(FloatFieldSchema(StringName("score")), """{"score": "19.99"}"""))
    result shouldBe a[Failure[?]]
  }
}
