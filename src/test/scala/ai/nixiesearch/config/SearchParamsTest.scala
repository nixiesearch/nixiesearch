package ai.nixiesearch.config

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.nn.ModelRef
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticParams}

class SearchParamsTest extends AnyFlatSpec with Matchers {

  it should "decode lexical as object" in {
    val yaml =
      """type: text
        |search:
        |  lexical:
        |""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(name = StringName("field"), search = SearchParams(lexical = Some(LexicalParams())))
    )
  }

  it should "decode hybrid as object" in {
    val yaml =
      """type: text
        |search:
        |  lexical:
        |  semantic:
        |    model: text
        |""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(
        name = StringName("field"),
        search = SearchParams(semantic = Some(SemanticParams(ModelRef("text"))), lexical = Some(LexicalParams()))
      )
    )
  }

  it should "decode semantic with options" in {
    val yaml =
      """type: text
        |search:
        |  lexical:
        |    analyze: english
        |  semantic:
        |    model: text""".stripMargin
    val result = decodeYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(
        name = StringName("field"),
        search = SearchParams(
          semantic = Some(SemanticParams(ModelRef("text"))),
          lexical = Some(LexicalParams(analyze = English))
        )
      )
    )
  }

  def decodeYaml(yaml: String): Either[Throwable, FieldSchema[? <: Field]] = {
    implicit val decoder: Decoder[FieldSchema[? <: Field]] = FieldSchema.yaml.fieldSchemaDecoder(StringName("field"))
    parse(yaml).flatMap(_.as[FieldSchema[? <: Field]])
  }
}
