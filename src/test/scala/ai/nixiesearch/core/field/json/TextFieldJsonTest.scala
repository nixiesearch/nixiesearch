package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticSimpleParams
import ai.nixiesearch.core.{Document, DocumentDecoder, Field}
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.github.plokhotnyuk.jsoniter_scala.core.*

import scala.util.{Failure, Try}

class TextFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode plain strings" in {
    val result = decode(TextFieldSchema(StringName("name")), """{"name": "value"}""")
    result shouldBe Some(TextField("name", "value"))
  }

  it should "fail on type=int" in {
    val result = Try(decode(TextFieldSchema(StringName("name")), """{"name": 1}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on type=[]" in {
    val result = Try(decode(TextFieldSchema(StringName("name")), """{"name": ["value"]}"""))
    result shouldBe a[Failure[?]]
  }

  it should "decode pre-embedded" in {
    val semantic = SemanticSimpleParams(dim = 3)
    val result   = decode(
      TextFieldSchema(StringName("name"), search = SearchParams(semantic = Some(semantic))),
      json = """{"name": {"text": "value", "embedding": [1,2,3]}}"""
    ).get
    result.name shouldBe "name"
    result.value shouldBe "value"
    result.embedding.get should equal(Array(1.0f, 2.0f, 3.0f))
  }

  it should "fail on dim mismatch" in {
    val semantic = SemanticSimpleParams(dim = 3)
    val result   = Try(
      decode(
        TextFieldSchema(StringName("name"), search = SearchParams(semantic = Some(semantic))),
        json = """{"name": {"text": "value", "embedding": [1,2,3,4]}}"""
      )
    )
    result shouldBe a[Failure[?]]
  }

  it should "not accept null values" in {
    val result = Try(decode(TextFieldSchema(StringName("title"), required = true), """{"title": null}"""))
    result shouldBe a[Failure[?]]
  }
}
