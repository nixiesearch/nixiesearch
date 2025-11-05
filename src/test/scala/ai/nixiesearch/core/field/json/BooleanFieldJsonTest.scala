package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.BooleanFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.BooleanField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class BooleanFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode true" in {
    val result = decode(BooleanFieldSchema(StringName("flag")), """{"flag": true}""")
    result shouldBe Some(BooleanField("flag", true))
  }

  it should "decode false" in {
    val result = decode(BooleanFieldSchema(StringName("flag")), """{"flag": false}""")
    result shouldBe Some(BooleanField("flag", false))
  }

  it should "fail on string values" in {
    val result = Try(decode(BooleanFieldSchema(StringName("flag")), """{"flag": "true"}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on int values" in {
    val result = Try(decode(BooleanFieldSchema(StringName("flag")), """{"flag": 1}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on null values" in {
    val result = Try(decode(BooleanFieldSchema(StringName("flag")), """{"flag": null}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on array values" in {
    val result = Try(decode(BooleanFieldSchema(StringName("flag")), """{"flag": [true]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on object values" in {
    val result = Try(decode(BooleanFieldSchema(StringName("flag")), """{"flag": {}}"""))
    result shouldBe a[Failure[?]]
  }
}
