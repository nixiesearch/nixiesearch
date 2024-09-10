package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.GenInferenceModelConfig.LLMPromptTemplate.Qwen2Template
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, GenInferenceModelConfig, PromptConfig}
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

    /** handle: ModelHandle, prompt: LLMPromptTemplate, system: Option[String] = None, file: Option[String] = None, gpu:
      * Boolean = false
      */
    val text =
      """generative:
        |  qwen2:
        |    handle: Qwen/Qwen2-0.5B-Instruct-GGUF
        |    file: qwen2-0_5b-instruct-q4_0.gguf
        |    prompt: qwen2
        |    gpu: true
        |""".stripMargin
    val decoded = parseYaml(text).flatMap(_.as[InferenceConfig])
    decoded shouldBe Right(
      InferenceConfig(generative =
        Map(
          ModelRef("qwen2") -> GenInferenceModelConfig(
            model = ModelHandle.HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF"),
            file = Some("qwen2-0_5b-instruct-q4_0.gguf"),
            prompt = Qwen2Template,
            gpu = true
          )
        )
      )
    )
  }
}
