package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
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
        |      doc: "passage: {doc}"
        |      query: "query: {query}"
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = PromptConfig(doc = "passage: {doc}", query = "query: {query}")
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
