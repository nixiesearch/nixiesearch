package ai.nixiesearch.api

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InferenceRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import InferenceRoute.{given, *}
  import ai.nixiesearch.util.HttpTest.*
  val docs               = Nil
  val mapping            = TestIndexMapping()
  override val inference = TestInferenceConfig.full()

  it should "embed documents" in withIndex { index =>
    {
      val response =
        send[EmbeddingInferenceRequest, EmbeddingInferenceResponse](
          InferenceRoute(index.indexer.index.models).routes,
          "http://localhost/inference/embedding/text",
          Some(EmbeddingInferenceRequest(List(EmbeddingDocument("hello world")))),
          Method.POST
        )
      response.output.size shouldBe 1
    }
  }

  it should "generate completions" in withIndex { index =>
    {
      val response =
        send[CompletionRequest, CompletionResponse](
          InferenceRoute(index.indexer.index.models).routes,
          "http://localhost/inference/completion/qwen2",
          Some(CompletionRequest(prompt = "why did chicken cross the road? answer short.", maxTokens = 10)),
          Method.POST
        )
      response.output shouldBe "Chicken was cross the road because it was hungry."
    }
  }
}
