package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.field.TextListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TextListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode plain text[]" in {
    val result = decode(TextListFieldSchema(StringName("tracks")), """{"tracks": ["foo", "bar"]}""")
    result shouldBe TextListField("tracks", List("foo", "bar"))
  }

  it should "decode pre-embedded text[]" in {
    val result = decode(
      TextListFieldSchema(StringName("tracks")),
      """{"tracks": {"text": ["a","b"], "embedding": [[1.0,2.0,3.0], [4.0,5.0,6.0]}}"""
    ).asInstanceOf[TextListField]
    result.name shouldBe "tracks"
    result.value shouldBe List("a", "b")
    result.embeddings.get(0) should equal(Array(1.0f, 2.0f, 3.0f))
  }
}
