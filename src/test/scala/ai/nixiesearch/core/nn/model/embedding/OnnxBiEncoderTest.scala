package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.{GenInferenceModelConfig, PromptConfig}
import ai.nixiesearch.config.InferenceConfig.GenInferenceModelConfig.LLMPromptTemplate.Qwen2Template
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.DistanceFunction.CosineDistance
import ai.nixiesearch.core.nn.model.ModelFileCache
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Paths}

class OnnxBiEncoderTest extends AnyFlatSpec with Matchers {
  it should "match minilm on python" in {
    val inference = OnnxEmbeddingInferenceModelConfig(
      model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
      prompt = PromptConfig(
        query = "query: ",
        doc = "doc: "
      )
    )
    val handle = HuggingFaceHandle("nixiesearch", "all-MiniLM-L6-v2-onnx")
    val (embedder, shutdownHandle) = EmbedModelDict
      .createHuggingface(handle, inference, ModelFileCache(Paths.get("/tmp/")))
      .allocated
      .unsafeRunSync()
    val result = embedder
      .encodeDocuments(
        List(
          "How many people live in Berlin?",
          "Berlin is well known for its museums.",
          "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
        )
      )
      .unsafeRunSync()
    val d1 = CosineDistance.dist(result(0), result(1))
    d1 shouldBe 0.62f +- 0.02
    val d2 = CosineDistance.dist(result(0), result(2))
    d2 shouldBe 0.77f +- 0.02
    shutdownHandle.unsafeRunSync()
  }
}
