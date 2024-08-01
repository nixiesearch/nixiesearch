package ai.nixiesearch.config

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, ModelPrefix, NoSearch, SemanticSearch}
import ai.nixiesearch.core.Field
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*

class SearchTypeTest extends AnyFlatSpec with Matchers {
  it should "decode non-searchable fields" in {
    val result = decodeYaml("type: text\nsearch: false")
    result shouldBe Right(TextFieldSchema(name = "field", search = NoSearch))
  }

  it should "decode lexical as string" in {
    val result = decodeYaml("type: text\nsearch: lexical")
    result shouldBe Right(TextFieldSchema(name = "field", search = LexicalSearch()))
  }

  it should "decode hybrid as string" in {
    val result = decodeYaml("type: text\nsearch: hybrid")
    result shouldBe Right(TextFieldSchema(name = "field", search = HybridSearch()))
  }

  it should "decode lexical as object" in {
    val yaml =
      """type: text
        |search:
        |  type: lexical""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(TextFieldSchema(name = "field", search = LexicalSearch()))
  }

  it should "decode hybrid as object" in {
    val yaml =
      """type: text
        |search:
        |  type: hybrid""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(TextFieldSchema(name = "field", search = HybridSearch()))
  }

  it should "decode semantic with options" in {
    val yaml =
      """type: text
        |search:
        |  type: semantic
        |  language: english""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(name = "field", search = SemanticSearch(prefix = ModelPrefix("query: ", "passage: ")))
    )
  }

  def decodeYaml(yaml: String): Either[Throwable, FieldSchema[? <: Field]] = {
    implicit val decoder: Decoder[FieldSchema[? <: Field]] = FieldSchema.yaml.fieldSchemaDecoder("field")
    parse(yaml).flatMap(_.as[FieldSchema[? <: Field]])
  }
}
