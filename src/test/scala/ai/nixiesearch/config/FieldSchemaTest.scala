package ai.nixiesearch.config

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.Language.English
import ai.nixiesearch.config.SearchType.NoSearch
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

  def parseYaml(yaml: String): Either[Throwable, FieldSchema[_ <: Field]] = {
    implicit val decoder: Decoder[FieldSchema[_ <: Field]] = FieldSchema.yaml.fieldSchemaDecoder("field")
    parse(yaml).flatMap(_.as[FieldSchema[_ <: Field]])
  }
}
