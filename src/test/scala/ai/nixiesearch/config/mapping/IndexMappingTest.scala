package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.util.Try

class IndexMappingTest extends AnyFlatSpec with Matchers {
  it should "create mapping from document with string fields" in {
    val result = IndexMapping.fromDocument(List(Document(List(TextField("title", "yo")))), "test").unsafeRunSync()
    result shouldBe IndexMapping(
      name = "test",
      fields = List(
        TextFieldSchema("title", search = LexicalSearch(), sort = true, facet = true, filter = true)
      )
    )
  }
  "migration" should "preserve compatible int fields" in {
    val before = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "fail on incompatible migrations" in {
    val before = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping("foo", fields = Map("test" -> TextFieldSchema("test")))
    val result = Try(before.migrate(after).unsafeRunSync())
    result.isFailure shouldBe true
  }

  it should "add+remove fields" in {
    val before = IndexMapping("foo", fields = Map("test1" -> IntFieldSchema("test1")))
    val after  = IndexMapping("foo", fields = Map("test2" -> IntFieldSchema("test2")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  "dynamic mapping" should "update existing mapping with new fields" in {
    val before = IndexMapping("foo", fields = Map("test1" -> IntFieldSchema("test1")))
    val after  = IndexMapping("foo", fields = Map("test2" -> IntFieldSchema("test2")))
    val result = before.dynamic(after).unsafeRunSync()
    result shouldBe IndexMapping("foo", fields = before.fields ++ after.fields)
  }

  it should "accept the same field" in {
    val before = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  "yaml decoder" should "add an implicit id field mapping" in {
    val yaml =
      """
        |alias: prod
        |fields:
        |  title:
        |    type: text
        |    search: false""".stripMargin
    val decoder = IndexMapping.yaml.indexMappingDecoder("test")
    val json    = io.circe.yaml.parser.parse(yaml).flatMap(_.as[IndexMapping](decoder))
    json shouldBe Right(
      IndexMapping(
        name = "test",
        alias = List(Alias("prod")),
        fields = Map(
          "id"    -> TextFieldSchema("id", filter = true),
          "title" -> TextFieldSchema("title")
        )
      )
    )
  }
}
