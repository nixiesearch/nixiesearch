package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.SearchType.{ModelPrefix, SemanticSearch}
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

  it should "parse distributed config" in {
    val yaml   = IOUtils.resourceToString("/config/distributed.yml", StandardCharsets.UTF_8)
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed shouldBe Right(
      Config(
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
              )
            ),
            store = DistributedStoreConfig(
              searcher = MemoryLocation(),
              indexer = DiskLocation(Paths.get("/path/to/index")),
              remote = S3Location(
                bucket = "index-bucket",
                prefix = "foo/bar",
                region = Some("us-east-1"),
                endpoint = Some("http://localhost:8443/")
              )
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
