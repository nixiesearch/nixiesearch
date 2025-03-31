package ai.nixiesearch.config

import ai.nixiesearch.config.EmbedCacheConfig.{MemoryCacheConfig, NoCache}
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.{
  LlamacppInferenceModelConfig,
  LlamacppParams
}
import ai.nixiesearch.config.InferenceConfig.{
  CompletionInferenceModelConfig,
  EmbeddingInferenceModelConfig,
  PromptConfig
}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.CohereEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig.OnnxModelFile
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.PoolingType.MeanPooling
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse as parseYaml

class InferenceConfigTest extends AnyFlatSpec with Matchers {
  it should "parse embedding config" in {
    val text =
      """embedding:
        |  small:
        |    provider: onnx
        |    model: nixiesearch/e5-small-v2-onnx
        |    prompt:
        |      doc: "passage: "
        |      query: "query: "
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
          )
        )
      )
    )
  }
  it should "parse minified embedding" in {
    val text =
      """embedding:
        |  small:
        |    model: nixiesearch/e5-small-v2-onnx
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
          )
        )
      )
    )
  }
  it should "parse openai embedding" in {
    val text =
      """embedding:
        |  small:
        |    model: text-embedding-3-small
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OpenAIEmbeddingInferenceModelConfig(
            model = "text-embedding-3-small"
          )
        )
      )
    )
  }
  it should "parse cohere embedding" in {
    val text =
      """embedding:
        |  small:
        |    provider: cohere
        |    model: embed-english-v3.0
        |    batch_size: 128
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> CohereEmbeddingInferenceModelConfig(
            model = "embed-english-v3.0",
            batchSize = 128
          )
        )
      )
    )
  }

  it should "parse cohere embedding with no embed cache" in {
    val text =
      """embedding:
        |  small:
        |    provider: cohere
        |    model: embed-english-v3.0
        |    batch_size: 128
        |    cache: false
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> CohereEmbeddingInferenceModelConfig(
            model = "embed-english-v3.0",
            batchSize = 128,
            cache = NoCache
          )
        )
      )
    )
  }
  it should "parse cohere embedding with inmem embed cache" in {
    val text =
      """embedding:
        |  small:
        |    provider: cohere
        |    model: embed-english-v3.0
        |    batch_size: 128
        |    cache:
        |      memory:
        |        max_size: 1000
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> CohereEmbeddingInferenceModelConfig(
            model = "embed-english-v3.0",
            batchSize = 128,
            cache = MemoryCacheConfig(1000)
          )
        )
      )
    )
  }
  it should "parse minified embedding with pooling and norm" in {
    val text =
      """embedding:
        |  small:
        |    model: nixiesearch/e5-small-v2-onnx
        |    pooling: mean
        |    normalize: false
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            pooling = MeanPooling,
            prompt = PromptConfig.E5,
            normalize = false
          )
        )
      )
    )
  }

  it should "parse embedding config with onnx file override, string" in {
    val text =
      """embedding:
        |  small:
        |    provider: onnx
        |    model: nixiesearch/e5-small-v2-onnx
        |    file: test.onnx
        |    prompt:
        |      doc: "passage: "
        |      query: "query: "
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = PromptConfig.E5,
            file = Some(OnnxModelFile("test.onnx")),
            pooling = MeanPooling
          )
        )
      )
    )
  }
  it should "parse embedding config with onnx file override, obj" in {
    val text =
      """embedding:
        |  small:
        |    provider: onnx
        |    model: nixiesearch/e5-small-v2-onnx
        |    file:
        |      base: test.onnx
        |      data: test.onnx_data
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = PromptConfig.E5,
            file = Some(OnnxModelFile("test.onnx", Some("test.onnx_data"))),
            pooling = MeanPooling
          )
        )
      )
    )
  }

  it should "parse generative config" in {
    val text =
      """completion:
        |  qwen2:
        |    provider: llamacpp
        |    model: Qwen/Qwen2-0.5B-Instruct-GGUF
        |    file: qwen2-0_5b-instruct-q4_0.gguf
        |    prompt: qwen2
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(completion =
        Map(
          ModelRef("qwen2") -> LlamacppInferenceModelConfig(
            model = ModelHandle.HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF"),
            file = Some("qwen2-0_5b-instruct-q4_0.gguf")
          )
        )
      )
    )
  }

  it should "parse generative config with options" in {
    val text =
      """completion:
        |  qwen2:
        |    provider: llamacpp
        |    model: Qwen/Qwen2-0.5B-Instruct-GGUF
        |    file: qwen2-0_5b-instruct-q4_0.gguf
        |    prompt: qwen2
        |    options:
        |      flash_attn: false
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(completion =
        Map(
          ModelRef("qwen2") -> LlamacppInferenceModelConfig(
            model = ModelHandle.HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF"),
            file = Some("qwen2-0_5b-instruct-q4_0.gguf"),
            options = LlamacppParams(flash_attn = false)
          )
        )
      )
    )
  }
}
