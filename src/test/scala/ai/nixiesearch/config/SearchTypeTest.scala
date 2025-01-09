package ai.nixiesearch.config

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, ModelPrefix, NoSearch, SemanticSearch}
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.nn.ModelRef
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import ai.nixiesearch.config.mapping.FieldName.StringName

class SearchTypeTest extends AnyFlatSpec with Matchers {

  it should "decode lexical as object" in {
    val yaml =
      """type: text
        |search:
        |  type: lexical""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(TextFieldSchema(name = StringName("field"), search = LexicalSearch()))
  }

  it should "decode hybrid as object" in {
    val yaml =
      """type: text
        |search:
        |  type: hybrid
        |  model: text""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(TextFieldSchema(name = StringName("field"), search = HybridSearch(ModelRef("text"))))
  }

  it should "decode semantic with options" in {
    val yaml =
      """type: text
        |search:
        |  type: semantic
        |  language: english
        |  model: text""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(TextFieldSchema(name = StringName("field"), search = SemanticSearch(ModelRef("text"))))
  }

  def decodeYaml(yaml: String): Either[Throwable, FieldSchema[? <: Field]] = {
    implicit val decoder: Decoder[FieldSchema[? <: Field]] = FieldSchema.yaml.fieldSchemaDecoder(StringName("field"))
    parse(yaml).flatMap(_.as[FieldSchema[? <: Field]])
  }
}
