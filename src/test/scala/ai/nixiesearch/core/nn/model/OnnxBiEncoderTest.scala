package ai.nixiesearch.core.nn.model

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.DistanceFunction.CosineDistance
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class OnnxBiEncoderTest extends AnyFlatSpec with Matchers {
  it should "match minilm on python" in {
    val handle  = HuggingFaceHandle("nixiesearch", "all-MiniLM-L6-v2-onnx")
    val session = OnnxSession.load(handle).unsafeRunSync()
    val enc     = OnnxBiEncoder(session)
    val result = enc.embed(
      Array(
        "How many people live in Berlin?",
        "Berlin is well known for its museums.",
        "Berlin had a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."
      )
    )
    val d1 = CosineDistance.dist(result(0), result(1))
    d1 shouldBe 0.539f +- 0.001
    val d2 = CosineDistance.dist(result(0), result(2))
    d2 shouldBe 0.738f +- 0.001
  }
}
