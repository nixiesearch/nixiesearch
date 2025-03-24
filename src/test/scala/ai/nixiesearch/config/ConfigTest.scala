package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.InferenceConfig.PromptConfig
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchType.{ModelPrefix, SemanticSearch}
import ai.nixiesearch.config.mapping.SuggestSchema.Lemmatize
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SuggestSchema}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import org.apache.commons.io.IOUtils
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "parse sample config" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed shouldBe Right(
      Config(
        inference = InferenceConfig(
          embedding = Map(
            ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
              model = ModelHandle.HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
            )
          )
        ),
        indexer = IndexerConfig(),
        core = CoreConfig(
          host = Hostname("0.0.0.0"),
          port = Port(8080),
          cache = CacheConfig(
            dir = "/cache"
          )
        ),
        searcher = SearcherConfig(),
        schema = Map(
          IndexName.unsafe("helloworld") -> IndexMapping(
            name = IndexName.unsafe("helloworld"),
            alias = Nil,
            fields = Map(
              StringName("_id") -> TextFieldSchema(name = StringName("_id"), filter = true),
              StringName("title") -> TextFieldSchema(
                name = StringName("title"),
                search = SemanticSearch(model = ModelRef("text"))
              ),
              StringName("desc") -> TextFieldSchema(
                name = StringName("desc"),
                search = SemanticSearch(model = ModelRef("text"))
              ),
              StringName("price") -> IntFieldSchema(
                name = StringName("price"),
                filter = true,
                facet = true,
                sort = true
              )
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
        core = CoreConfig(host = Hostname("0.0.0.0"), port = Port(8080)),
        inference = InferenceConfig(
          embedding = Map(
            ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
              model = ModelHandle.HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
            )
          )
        ),
        indexer = IndexerConfig(),
        searcher = SearcherConfig(),
        schema = Map(
          IndexName.unsafe("helloworld") -> IndexMapping(
            name = IndexName.unsafe("helloworld"),
            alias = Nil,
            fields = Map(
              StringName("_id") -> TextFieldSchema(name = StringName("_id"), filter = true),
              StringName("title") -> TextFieldSchema(
                name = StringName("title"),
                search = SemanticSearch(model = ModelRef("text"))
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
    parse(yaml).flatMap(_.as[Config]) shouldBe Right(Config())
  }

  it should "not fail on no schemas" in {
    val yaml = """
                 |searcher:
                 |  api:
                 |    host: localhost
                 |    port: 8080
                 |
                 |
                 |schema:""".stripMargin
    parse(yaml).flatMap(_.as[Config]) shouldBe Right(Config())
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
        core = CoreConfig(host = Hostname("0.0.0.0"), port = Port(8080)),
        inference = InferenceConfig(
          embedding = Map(
            ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
              model = ModelHandle.HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
            )
          )
        ),
        indexer = IndexerConfig(),
        searcher = SearcherConfig(),
        schema = Map(
          IndexName.unsafe("helloworld") -> IndexMapping(
            name = IndexName.unsafe("helloworld"),
            alias = Nil,
            fields = Map(
              StringName("_id") -> TextFieldSchema(name = StringName("_id"), filter = true),
              StringName("title1") -> TextFieldSchema(
                name = StringName("title1"),
                search = SemanticSearch(model = ModelRef("text")),
                suggest = Some(SuggestSchema())
              ),
              StringName("title2") -> TextFieldSchema(
                name = StringName("title2"),
                language = English,
                search = SemanticSearch(model = ModelRef("text")),
                suggest = Some(
                  SuggestSchema(
                    lemmatize = Some(Lemmatize(LocalURL(Paths.get("/path/to/lemmas.csv"))))
                  )
                )
              ),
              StringName("desc") -> TextFieldSchema(
                name = StringName("desc"),
                search = SemanticSearch(model = ModelRef("text"))
              ),
              StringName("price") -> IntFieldSchema(
                name = StringName("price"),
                filter = true,
                facet = true,
                sort = true
              )
            )
          )
        )
      )
    )
  }

}
