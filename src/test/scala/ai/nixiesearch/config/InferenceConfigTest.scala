package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.{
  OnnxEmbeddingInferenceModelConfig,
  OnnxModelFile
}
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
        |      doc: "passage: "
        |      query: "query: "
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = Some(PromptConfig(doc = "passage: ", query = "query: "))
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
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = None
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
        |      doc: "passage: {doc}"
        |      query: "query: {query}"
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(
        embedding = Map(
          ModelRef("small") -> OnnxEmbeddingInferenceModelConfig(
            model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
            prompt = Some(PromptConfig(doc = "passage: {doc}", query = "query: {query}")),
            file = Some(OnnxModelFile("test.onnx"))
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
            prompt = Some(PromptConfig(doc = "passage: {doc}", query = "query: {query}")),
            file = Some(OnnxModelFile("test.onnx", Some("test.onnx_data")))
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
