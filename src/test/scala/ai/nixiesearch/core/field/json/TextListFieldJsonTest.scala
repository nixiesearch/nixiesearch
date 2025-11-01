package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticSimpleParams
import ai.nixiesearch.core.Field.TextListField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class TextListFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode plain text[]" in {
    val result = decode(TextListFieldSchema(StringName("tracks")), """{"tracks": ["foo", "bar"]}""")
    result shouldBe Some(TextListField("tracks", List("foo", "bar")))
  }

  it should "decode pre-embedded text[]" in {
    val result = decode(
      TextListFieldSchema(StringName("tracks")),
      """{"tracks": {"text": ["a","b"], "embedding": [[1.0,2.0,3.0], [4.0,5.0,6.0]]}}"""
    ).get.asInstanceOf[TextListField]
    result.name shouldBe "tracks"
    result.value shouldBe List("a", "b")
    result.embeddings.get(0) should equal(Array(1.0f, 2.0f, 3.0f))
  }

  it should "fail on arrays of nulls" in {
    val result = Try(decode(TextListFieldSchema(StringName("tracks")), """{"tracks": [null]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on nested ints" in {
    val result = Try(decode(TextListFieldSchema(StringName("tracks")), """{"tracks": [1,2,3]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on bool arrays" in {
    val result = Try(decode(TextListFieldSchema(StringName("title")), """{"title": [true, false]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "decode pre-embedded text[1] emb[2]" in {
    val semantic = SemanticSimpleParams()
    val result   = decode(
      TextListFieldSchema(StringName("titles"), search = SearchParams(semantic = Some(semantic))),
      """{"titles": {"text": ["foo"], "embedding": [[1,2,3],[4,5,6]]}}"""
    ).get
    result.name shouldBe "titles"
    result.value shouldBe List("foo")
    result.embeddings.get.length shouldBe 2
  }

  it should "decode pre-embedded text[1] emb[1]" in {
    val semantic = SemanticSimpleParams()
    val result   = decode(
      TextListFieldSchema(StringName("titles"), search = SearchParams(semantic = Some(semantic))),
      """{"titles": {"text": ["foo"], "embedding": [[1,2,3]]}}"""
    ).get
    result.name shouldBe "titles"
    result.value shouldBe List("foo")
    result.embeddings.get.length shouldBe 1
  }

  it should "fail on pre-embedded text[2] emb[1]" in {
    val semantic = SemanticSimpleParams()
    val result   = Try(
      decode(
        TextListFieldSchema(StringName("titles"), search = SearchParams(semantic = Some(semantic))),
        """{"titles": {"text": ["foo", "bar"], "embedding": [[1,2,3]]}}"""
      )
    )
    result shouldBe a[Failure[?]]
  }
}
