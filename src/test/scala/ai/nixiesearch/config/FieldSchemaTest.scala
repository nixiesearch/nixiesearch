package ai.nixiesearch.config

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.{Language, SuggestSchema}
import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.config.mapping.SuggestSchema.Expand
import ai.nixiesearch.core.Field
import io.circe.Decoder
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FieldSchemaTest extends AnyFlatSpec with Matchers {
  it should "decode text field schema" in {
    val yaml =
      """type: text
        |search: false
        |facet: true
        |filter: true
        |store: false""".stripMargin
    val result = parseYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(
        name = "field",
        search = NoSearch,
        store = false,
        filter = true,
        facet = true
      )
    )
  }

  it should "decode int field schema" in {
    val yaml =
      """type: int
        |facet: true
        |filter: true
        |store: false""".stripMargin
    val result = parseYaml(yaml)
    result shouldBe Right(
      IntFieldSchema(
        name = "field",
        store = false,
        filter = true,
        facet = true
      )
    )
  }

  it should "decode int field schema with all default fields" in {
    val yaml   = """type: int""".stripMargin
    val result = parseYaml(yaml)
    result shouldBe Right(IntFieldSchema(name = "field"))
  }

  it should "decode fields with simple suggest mapping" in {
    val yaml =
      """type: text
        |search: false
        |suggest: true
        """.stripMargin
    val result = parseYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(
        name = "field",
        search = NoSearch,
        store = true,
        filter = false,
        facet = false,
        language = Language.Generic,
        suggest = Some(SuggestSchema())
      )
    )
  }

  it should "decode fields with extended suggest mapping" in {
    val yaml =
      """type: text
        |search: false
        |suggest:
        |  lowercase: true
        |  expand:
        |    min-terms: 1
        |    max-terms: 5
        """.stripMargin
    val result = parseYaml(yaml)
    result shouldBe Right(
      TextFieldSchema(
        name = "field",
        search = NoSearch,
        store = true,
        filter = false,
        facet = false,
        language = Language.Generic,
        suggest = Some(
          SuggestSchema(
            lowercase = true,
            expand = Some(Expand(1, 5))
          )
        )
      )
    )
  }

  def parseYaml(yaml: String): Either[Throwable, FieldSchema[? <: Field]] = {
    implicit val decoder: Decoder[FieldSchema[? <: Field]] = FieldSchema.yaml.fieldSchemaDecoder("field")
    parse(yaml).flatMap(_.as[FieldSchema[? <: Field]])
  }
}
