package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.nn.ModelRef
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams

import scala.util.Try
import io.circe.syntax.*
import io.circe.parser.*

class IndexMappingTest extends AnyFlatSpec with Matchers {
  "migration" should "preserve compatible int fields" in {
    val before = IndexMapping(IndexName("foo"), fields = Map(StringName("test") -> IntFieldSchema(StringName("test"))))
    val after  = IndexMapping(IndexName("foo"), fields = Map(StringName("test") -> IntFieldSchema(StringName("test"))))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "fail on incompatible migrations" in {
    val before = IndexMapping(IndexName("foo"), fields = Map(StringName("test") -> IntFieldSchema(StringName("test"))))
    val after  = IndexMapping(IndexName("foo"), fields = Map(StringName("test") -> TextFieldSchema(StringName("test"))))
    val result = Try(before.migrate(after).unsafeRunSync())
    result.isFailure shouldBe true
  }

  it should "add+remove fields" in {
    val before =
      IndexMapping(IndexName("foo"), fields = Map(StringName("test1") -> IntFieldSchema(StringName("test1"))))
    val after = IndexMapping(IndexName("foo"), fields = Map(StringName("test2") -> IntFieldSchema(StringName("test2"))))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "encode-decode a json schema" in {
    import IndexMapping.json.given
    val mapping = IndexMapping(
      name = IndexName("foo"),
      alias = List(Alias("bar")),
      config = IndexConfig(),
      fields = Map(
        StringName("text") -> TextFieldSchema(
          StringName("text"),
          search = SearchParams(lexical = Some(LexicalParams()))
        ),
        StringName("int") -> IntFieldSchema(StringName("int"), facet = true)
      )
    )
    val json    = mapping.asJson.noSpaces
    val decoded = decode[IndexMapping](json)
    decoded shouldBe Right(mapping)
  }

  it should "get field schema for a type" in {
    val mapping = IndexMapping(
      name = IndexName("foo"),
      alias = List(Alias("bar")),
      config = IndexConfig(),
      fields = Map(
        StringName("text") -> TextFieldSchema(
          StringName("text"),
          search = SearchParams(lexical = Some(LexicalParams()))
        ),
        StringName("int") -> IntFieldSchema(StringName("int"), facet = true)
      )
    )
    val schemaOK = mapping.fieldSchema[IntFieldSchema](StringName("int"))
    schemaOK shouldBe Some(IntFieldSchema(StringName("int"), facet = true))
    val schemaFail = mapping.fieldSchema[TextFieldSchema](StringName("int"))
    schemaFail shouldBe None
  }

  "yaml decoder" should "add an implicit id field mapping" in {
    val yaml =
      """
        |alias: prod
        |fields:
        |  title:
        |    type: text
        |    search: false""".stripMargin
    val decoder = IndexMapping.yaml.indexMappingDecoder(IndexName("test"))
    val json    = io.circe.yaml.parser.parse(yaml).flatMap(_.as[IndexMapping](using decoder))
    json shouldBe Right(
      IndexMapping(
        name = IndexName("test"),
        alias = List(Alias("prod")),
        fields = Map(
          StringName("_id")   -> TextFieldSchema(StringName("_id"), filter = true),
          StringName("title") -> TextFieldSchema(StringName("title"))
        )
      )
    )
  }

  it should "fail if wildcard field overrides regular field" in {
    val yaml =
      """
        |fields:
        |  tit*:
        |    type: text
        |    search: false
        |  title:
        |    type: text
        |    search: false""".stripMargin
    val decoder = IndexMapping.yaml.indexMappingDecoder(IndexName("test"))
    val json    = io.circe.yaml.parser.parse(yaml).flatMap(_.as[IndexMapping](using decoder))
    json shouldBe a[Left[?, ?]]

  }
}
