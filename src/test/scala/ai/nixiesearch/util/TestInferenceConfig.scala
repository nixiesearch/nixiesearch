package ai.nixiesearch.util

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.LlamacppInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.{CompletionInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig

object TestInferenceConfig {
  def full() = InferenceConfig(
    completion = Map(
      ModelRef("qwen2") -> LlamacppInferenceModelConfig(
        model = HuggingFaceHandle("Qwen", "Qwen2.5-0.5B-Instruct-GGUF"),
        file = Some("qwen2.5-0.5b-instruct-q4_0.gguf")
      )
    ),
    embedding = Map(
      ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
        model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
      )
    )
  )
  def empty()    = InferenceConfig()
  def semantic() = apply()
  def apply()    = InferenceConfig(
    embedding = Map(
      ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
        model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
      )
    )
  )
}
