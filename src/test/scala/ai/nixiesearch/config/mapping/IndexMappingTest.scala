package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexConfig.MappingConfig
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, SemanticSearch}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.util.Try
import io.circe.syntax.*
import io.circe.parser.*

class IndexMappingTest extends AnyFlatSpec with Matchers {
  "migration" should "preserve compatible int fields" in {
    val before = IndexMapping(IndexName("foo"), fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping(IndexName("foo"), fields = Map("test" -> IntFieldSchema("test")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "fail on incompatible migrations" in {
    val before = IndexMapping(IndexName("foo"), fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping(IndexName("foo"), fields = Map("test" -> TextFieldSchema("test")))
    val result = Try(before.migrate(after).unsafeRunSync())
    result.isFailure shouldBe true
  }

  it should "add+remove fields" in {
    val before = IndexMapping(IndexName("foo"), fields = Map("test1" -> IntFieldSchema("test1")))
    val after  = IndexMapping(IndexName("foo"), fields = Map("test2" -> IntFieldSchema("test2")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "encode-decode a json schema" in {
    import IndexMapping.json.given
    val mapping = IndexMapping(
      name = IndexName("foo"),
      alias = List(Alias("bar")),
      config = IndexConfig(mapping = MappingConfig(dynamic = true)),
      fields = Map(
        "text" -> TextFieldSchema("text", search = SemanticSearch()),
        "int"  -> IntFieldSchema("int", facet = true)
      )
    )
    val json    = mapping.asJson.noSpaces
    val decoded = decode[IndexMapping](json)
    decoded shouldBe Right(mapping)
  }

  it should "deduplicate same model handles" in {
    val mapping = IndexMapping(
      name = IndexName("foo"),
      fields = Map(
        "text1" -> TextFieldSchema("text1", search = SemanticSearch()),
        "text2" -> TextFieldSchema("text2", search = SemanticSearch()),
        "text3" -> TextFieldSchema("text3", search = SemanticSearch())
      )
    )
    mapping.modelHandles().size shouldBe 1
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
    val json    = io.circe.yaml.parser.parse(yaml).flatMap(_.as[IndexMapping](decoder))
    json shouldBe Right(
      IndexMapping(
        name = IndexName("test"),
        alias = List(Alias("prod")),
        fields = Map(
          "_id"   -> TextFieldSchema("_id", filter = true),
          "title" -> TextFieldSchema("title")
        )
      )
    )
  }
}
