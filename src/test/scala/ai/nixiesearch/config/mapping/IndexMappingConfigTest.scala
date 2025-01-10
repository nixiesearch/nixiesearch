package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexConfig.HnswConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class IndexMappingConfigTest extends AnyFlatSpec with Matchers {
  it should "parse hnsw config" in {
    val yaml =
      """
        |config:
        |  hnsw:
        |    m: 1
        |    efc: 2
        |    workers: 3
        |fields:
        |  title:
        |    type: text
        |    search: false""".stripMargin
    val decoder = IndexMapping.yaml.indexMappingDecoder(IndexName("test"))
    val json    = io.circe.yaml.parser.parse(yaml).flatMap(_.as[IndexMapping](decoder))
    json shouldBe Right(
      IndexMapping(
        name = IndexName("test"),
        config = IndexConfig(
          hnsw = HnswConfig(
            m = 1,
            efc = 2,
            workers = 3
          )
        ),
        fields = Map(
          StringName("_id")   -> TextFieldSchema(StringName("_id"), filter = true),
          StringName("title") -> TextFieldSchema(StringName("title"))
        )
      )
    )

  }

}
