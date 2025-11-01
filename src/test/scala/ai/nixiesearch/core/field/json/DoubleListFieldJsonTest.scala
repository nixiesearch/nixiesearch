package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.DoubleListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.DoubleListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class DoubleListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode double array" in {
    val result = decode(DoubleListFieldSchema(StringName("prices")), """{"prices": [1.5, 2.5, 3.5]}""")
    result shouldBe Some(DoubleListField("prices", List(1.5, 2.5, 3.5)))
  }

  it should "decode mixed int and double array" in {
    val result = decode(DoubleListFieldSchema(StringName("prices")), """{"prices": [1, 2.5, 3]}""")
    result shouldBe Some(DoubleListField("prices", List(1.0, 2.5, 3.0)))
  }

  it should "fail on non-numeric arrays" in {
    val result = Try(decode(DoubleListFieldSchema(StringName("prices")), """{"prices": ["1.5", "2.5"]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on null in array" in {
    val result = Try(decode(DoubleListFieldSchema(StringName("prices")), """{"prices": [1.5, null, 2.5]}"""))
    result shouldBe a[Failure[?]]
  }
}
