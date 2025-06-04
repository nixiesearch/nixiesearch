package ai.nixiesearch.core.nn.model.ranking

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.OnnxRankInferenceModelConfig
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import java.nio.file.Paths

class OnnxRankModelTest extends AnyFlatSpec with Matchers {
  it should "work with bert-based models" taggedAs (EndToEnd.Embeddings) in {
    val handle = HuggingFaceHandle("cross-encoder", "ms-marco-MiniLM-L6-v2")
    val config = OnnxRankInferenceModelConfig(model = handle)
    val (ranker, shutdownHandle) = OnnxRankModel
      .create(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch")))
      .allocated
      .unsafeRunSync()
    val result = ranker.scoreBatch("pizza", List("margherita", "dog poop")).unsafeRunSync().toArray
    result(0) should be > result(1)
    shutdownHandle.unsafeRunSync()
  }

}
