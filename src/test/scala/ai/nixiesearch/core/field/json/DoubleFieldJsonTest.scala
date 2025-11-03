package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.DoubleFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.DoubleField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class DoubleFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode double values" in {
    val result = decode(DoubleFieldSchema(StringName("price")), """{"price": 19.99}""")
    result shouldBe Some(DoubleField("price", 19.99))
  }

  it should "decode integer as double" in {
    val result = decode(DoubleFieldSchema(StringName("price")), """{"price": 20}""")
    result shouldBe Some(DoubleField("price", 20.0))
  }

  it should "decode negative values" in {
    val result = decode(DoubleFieldSchema(StringName("price")), """{"price": -19.99}""")
    result shouldBe Some(DoubleField("price", -19.99))
  }

  it should "fail on null values" in {
    val result = Try(decode(DoubleFieldSchema(StringName("price")), """{"price": null}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-numeric values" in {
    val result = Try(decode(DoubleFieldSchema(StringName("price")), """{"price": "19.99"}"""))
    result shouldBe a[Failure[?]]
  }
}
