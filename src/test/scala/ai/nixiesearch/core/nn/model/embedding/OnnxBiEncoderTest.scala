package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.DistanceFunction.CosineDistance
import ai.nixiesearch.core.nn.model.ModelFileCache
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.Files

class OnnxBiEncoderTest extends AnyFlatSpec with Matchers {
  it should "match minilm on python" in {
    val handle = HuggingFaceHandle("nixiesearch", "all-MiniLM-L6-v2-onnx")
    val (embedder, shutdownHandle) = EmbedModelDict
      .createHuggingface(handle, ModelFileCache(Files.createTempDirectory("onnx-cache")))
      .allocated
      .unsafeRunSync()
    val result = embedder
      .encode(
        List(
          "How many people live in Berlin?",
          "Berlin is well known for its museums.",
          "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
        )
      )
      .unsafeRunSync()
    val d1 = CosineDistance.dist(result(0), result(1))
    d1 shouldBe 0.54f +- 0.02
    val d2 = CosineDistance.dist(result(0), result(2))
    d2 shouldBe 0.74f +- 0.02
    shutdownHandle.unsafeRunSync()
  }
}
