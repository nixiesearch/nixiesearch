package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.SearchType.{ModelPrefix, SemanticSearch}
import ai.nixiesearch.config.StoreConfig.S3StoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.{LocalStoreUrl, S3StoreUrl}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "parse sample config" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed shouldBe Right(
      Config(
        store = S3StoreConfig(
          url = S3StoreUrl("bucket", "prefix"),
          workdir = Paths.get(System.getProperty("user.dir"))
        ),
        api = ApiConfig(host = Hostname("localhost")),
        search = Map(
          "helloworld" -> IndexMapping(
            name = "helloworld",
            alias = Nil,
            fields = Map(
              "_id" -> TextFieldSchema(name = "_id", filter = true),
              "title" -> TextFieldSchema(
                name = "title",
                search =
                  SemanticSearch(model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"), prefix = ModelPrefix.e5)
              ),
              "desc" -> TextFieldSchema(
                name = "desc",
                search =
                  SemanticSearch(model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"), prefix = ModelPrefix.e5)
              ),
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
