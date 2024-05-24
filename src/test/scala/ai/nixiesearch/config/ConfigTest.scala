package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchType.{ModelPrefix, SemanticSearch}
import ai.nixiesearch.config.mapping.SuggestSchema.Lemmatize
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestSchema}
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
        indexer = IndexerConfig(),
        searcher = SearcherConfig(Hostname("0.0.0.0"), Port(8080)),
        schema = Map(
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
        indexer = IndexerConfig(),
        searcher = SearcherConfig(Hostname("0.0.0.0"), Port(8080)),
        schema = Map(
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

  it should "fail on parse empty config" in {
    val yaml = "a:".stripMargin
    parse(yaml).flatMap(_.as[Config]) shouldBe a[Left[?, ?]]
  }

  it should "fail on no schemas" in {
    val yaml = """
                 |searcher:
                 |  api:
                 |    host: localhost
                 |    port: 8080
                 |
                 |
                 |schema:""".stripMargin
    parse(yaml).flatMap(_.as[Config]) shouldBe a[Left[?, ?]]
  }

  it should "fail on schema with no fields" in {
    val yaml = """searcher:
                 |  api:
                 |    host: localhost
                 |    port: 8080
                 |
                 |
                 |schema:
                 |  helloworld:
                 |    fields:
                 |""".stripMargin
    val result = parse(yaml).flatMap(_.as[Config])
    result shouldBe a[Left[?, ?]]
  }

  it should "parse suggest config" in {
    val yaml   = IOUtils.resourceToString("/config/suggest.yml", StandardCharsets.UTF_8)
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed shouldBe Right(
      Config(
        indexer = IndexerConfig(),
        searcher = SearcherConfig(Hostname("0.0.0.0"), Port(8080)),
        schema = Map(
          "helloworld" -> IndexMapping(
            name = "helloworld",
            alias = Nil,
            fields = Map(
              "_id" -> TextFieldSchema(name = "_id", filter = true),
              "title1" -> TextFieldSchema(
                name = "title1",
                search =
                  SemanticSearch(model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"), prefix = ModelPrefix.e5),
                suggest = Some(SuggestSchema())
              ),
              "title2" -> TextFieldSchema(
                name = "title2",
                language = English,
                search =
                  SemanticSearch(model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"), prefix = ModelPrefix.e5),
                suggest = Some(
                  SuggestSchema(
                    lemmatize = Some(Lemmatize(LocalURL(Paths.get("/path/to/lemmas.csv"))))
                  )
                )
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

}
