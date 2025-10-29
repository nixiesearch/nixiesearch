package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.IdFieldSchema
import ai.nixiesearch.core.field.IdField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IdFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "parse string field" in {
    val result = decode(IdFieldSchema(), """{"_id": "123"}""")
    result shouldBe IdField("_id", "123")
  }

  it should "parse int key" in {
    val result = decode(IdFieldSchema(), """{"_id": 123}""")
    result shouldBe IdField("_id", "123")
  }
}
