package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.{CompletionInferenceModelConfig, PromptConfig}
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
    val handle = HuggingFaceHandle("nixiesearch", "all-MiniLM-L6-v2-onnx")
    val config = OnnxEmbeddingInferenceModelConfig(
      model = handle,
      prompt = PromptConfig(
        query = "query: ",
        doc = "doc: "
      )
    )
    val (embedder, shutdownHandle) = EmbedModelDict
      .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/")))
      .allocated
      .unsafeRunSync()
    val result = embedder
      .encode(
        List(
          "query: How many people live in Berlin?",
          "passage: Berlin is well known for its museums.",
          "passage: Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
        )
      )
      .unsafeRunSync()
    val d1 = CosineDistance.dist(result(0), result(1))
    d1 shouldBe 0.46f +- 0.02
    val d2 = CosineDistance.dist(result(0), result(2))
    d2 shouldBe 0.77f +- 0.02
    shutdownHandle.unsafeRunSync()
  }

  it should "work with an XLM-based models" in {
    val handle = HuggingFaceHandle("nixiesearch", "multilingual-e5-base-onnx")
    val config = OnnxEmbeddingInferenceModelConfig(
      model = handle,
      prompt = PromptConfig(
        query = "query: ",
        doc = "passage: "
      )
    )
    val (embedder, shutdownHandle) = EmbedModelDict
      .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/")))
      .allocated
      .unsafeRunSync()
    val result = embedder.encode(List("query: test")).unsafeRunSync()
    result.length shouldBe 1
    result(0).length shouldBe 768
  }
}
