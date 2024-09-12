package ai.nixiesearch.util

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.GenInferenceModelConfig.LLMPromptTemplate.Qwen2Template
import ai.nixiesearch.config.InferenceConfig.{GenInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef

object TestInferenceConfig {
  def full() = InferenceConfig(
    generative = Map(
      ModelRef("qwen2") -> GenInferenceModelConfig(
        model = HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF"),
        file = Some("qwen2-0_5b-instruct-q4_0.gguf"),
        prompt = Qwen2Template
      )
    ),
    embedding = Map(
      ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
        model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
        prompt = PromptConfig(
          query = "query: ",
          doc = "doc: "
        )
      )
    )
  )
  def empty() = InferenceConfig()
  def apply() = InferenceConfig(
    embedding = Map(
      ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
        model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
        prompt = PromptConfig(
          query = "query: ",
          doc = "doc: "
        )
      )
    )
  )
}
