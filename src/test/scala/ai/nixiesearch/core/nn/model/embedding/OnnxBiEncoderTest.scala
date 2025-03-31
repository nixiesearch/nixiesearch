package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.InferenceConfig.{CompletionInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.DistanceFunction.CosineDistance
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.{Document, Query}
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.util.Tags.EndToEnd
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Paths}
import javax.print.Doc

class OnnxBiEncoderTest extends AnyFlatSpec with Matchers {
  it should "match minilm on python" in {
    val handle = HuggingFaceHandle("sentence-transformers", "all-MiniLM-L6-v2")
    val config = OnnxEmbeddingInferenceModelConfig(model = handle)
    val (embedder, shutdownHandle) = OnnxEmbedModel
      .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch/")))
      .allocated
      .unsafeRunSync()
    val query = embedder.encode(Query, List("How many people live in Berlin?")).compile.toList.unsafeRunSync()
    val docs = embedder
      .encode(
        Document,
        List(
          "Berlin is well known for its museums.",
          "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
        )
      )
      .compile.toList.unsafeRunSync()
    val d1 = CosineDistance.dist(query.head, docs(0))
    d1 shouldBe 0.53f +- 0.02
    val d2 = CosineDistance.dist(query.head, docs(1))
    d2 shouldBe 0.73f +- 0.02
    shutdownHandle.unsafeRunSync()
  }

  it should "work with an XLM-based models" taggedAs (EndToEnd.Embeddings) in {
    val handle = HuggingFaceHandle("intfloat", "multilingual-e5-base")
    val config = OnnxEmbeddingInferenceModelConfig(model = handle)
    val (embedder, shutdownHandle) = OnnxEmbedModel
      .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch")))
      .allocated
      .unsafeRunSync()
    val result = embedder.encode(Query, List("test")).compile.toList.unsafeRunSync()
    result.length shouldBe 1
    result(0).length shouldBe 768
    shutdownHandle.unsafeRunSync()
  }

}
