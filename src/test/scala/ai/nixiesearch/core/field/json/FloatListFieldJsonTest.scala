package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.FloatListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.FloatListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class FloatListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode float array" in {
    val result = decode(FloatListFieldSchema(StringName("scores")), """{"scores": [1.5, 2.5, 3.5]}""")
    result shouldBe Some(FloatListField("scores", List(1.5f, 2.5f, 3.5f)))
  }

  it should "decode mixed int and float array" in {
    val result = decode(FloatListFieldSchema(StringName("scores")), """{"scores": [1, 2.5, 3]}""")
    result shouldBe Some(FloatListField("scores", List(1.0f, 2.5f, 3.0f)))
  }

  it should "fail on non-numeric arrays" in {
    val result = Try(decode(FloatListFieldSchema(StringName("scores")), """{"scores": ["1.5", "2.5"]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on null in array" in {
    val result = Try(decode(FloatListFieldSchema(StringName("scores")), """{"scores": [1.5, null, 2.5]}"""))
    result shouldBe a[Failure[?]]
  }
}
