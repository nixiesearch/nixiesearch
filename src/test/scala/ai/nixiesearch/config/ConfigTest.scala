package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.IndexMapping.Alias
import ai.nixiesearch.config.SearchType.SemanticSearch
import ai.nixiesearch.config.StoreConfig.StoreUrl.{LocalStoreUrl, S3StoreUrl}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "parse sample config" in {
    val yaml = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    parse(yaml).flatMap(_.as[Config]) shouldBe Right(
      Config(
        store = StoreConfig(
          remote = S3StoreUrl("bucket", "prefix"),
          local = LocalStoreUrl("/var/lib/nixiesearch")
        ),
        api = ApiConfig(host = Hostname("localhost")),
        index = Map(
          "example" -> IndexMapping(
            name = "example",
            alias = List(Alias("prod")),
            fields = Map(
              "id"    -> TextFieldSchema(name = "id"),
              "title" -> TextFieldSchema(name = "title", search = SemanticSearch()),
              "desc"  -> TextFieldSchema(name = "desc", search = SemanticSearch()),
              "price" -> IntFieldSchema(name = "price", filter = true, facet = true, sort = true)
            )
          )
        )
      )
    )
  }

  it should "parse empty config" in {
    val yaml = "store:".stripMargin
    val p    = parse("nope:")
    parse(yaml).flatMap(_.as[Config]) shouldBe Right(Config())
  }
}
