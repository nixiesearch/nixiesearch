package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.IntListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.IntListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class IntListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode int array" in {
    val result = decode(IntListFieldSchema(StringName("counts")), """{"counts": [1, 2, 3]}""")
    result shouldBe Some(IntListField("counts", List(1, 2, 3)))
  }

  it should "decode negative values" in {
    val result = decode(IntListFieldSchema(StringName("counts")), """{"counts": [-1, 0, 1]}""")
    result shouldBe Some(IntListField("counts", List(-1, 0, 1)))
  }

  it should "fail on non-int arrays" in {
    val result = Try(decode(IntListFieldSchema(StringName("counts")), """{"counts": ["1", "2"]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on null in array" in {
    val result = Try(decode(IntListFieldSchema(StringName("counts")), """{"counts": [1, null, 2]}"""))
    result shouldBe a[Failure[?]]
  }
}
