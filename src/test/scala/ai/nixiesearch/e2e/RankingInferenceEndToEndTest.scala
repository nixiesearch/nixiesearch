package ai.nixiesearch.e2e

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.OnnxRankInferenceModelConfig
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import cats.effect.unsafe.implicits.global
import org.scalatest.prop.TableDrivenPropertyChecks.forAll

import java.nio.file.Paths

class RankingInferenceEndToEndTest extends AnyFlatSpec with Matchers {
  val models = Table(
    "model",
    "cross-encoder/ms-marco-MiniLM-L6-v2",
    "jinaai/jina-reranker-v2-base-multilingual"
  )

  it should "load cross-encoder models and score query-document pairs" taggedAs (EndToEnd.Embeddings) in {
    forAll(models) { model =>
      {
        val query     = "pizza"
        val documents = List("margherita pizza with tomato and cheese", "dog food")
        val scores    = RankingInferenceEndToEndTest.rank(model, query, documents)

        // First document (pizza) should score higher than second (dog food)
        scores.length shouldBe 2
        scores(0) should be > scores(1)
      }
    }
  }

}

object RankingInferenceEndToEndTest {
  def rank(model: String, query: String, documents: List[String]): Array[Float] = {
    val parts                    = model.split("/")
    val handle                   = HuggingFaceHandle(parts(0), parts(1))
    val config                   = OnnxRankInferenceModelConfig(model = handle)
    val (ranker, shutdownHandle) = OnnxRankModel
      .create(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch/")))
      .allocated
      .unsafeRunSync()
    val result = ranker.scoreBatch(query, documents).unsafeRunSync().toArray
    shutdownHandle.unsafeRunSync()
    result
  }
}
