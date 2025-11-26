package ai.nixiesearch.e2e

import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Raw
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.CohereEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.{CohereEmbedModel, OpenAIEmbedModel}
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import cats.effect.unsafe.implicits.global

class APIEmbedEndToEndTest extends AnyFlatSpec with Matchers {

  lazy val models = Table(
    ("provider", "model", "dims"),
    ("openai", "text-embedding-3-small", 1536),
    ("cohere", "embed-english-v3.0", 1024)
  )

  it should "load the model and embed" taggedAs (EndToEnd.APIEmbeddings) in {
    forAll(models) { (provider, model, dims) =>
      {
        val result = APIEmbedEndToEndTest.embed(provider, model, "query: test")
        result.length shouldBe dims
      }
    }
  }
}

object APIEmbedEndToEndTest {
  def embed(provider: String, model: String, text: String): Array[Float] = {
    val env               = EnvVars.load().unsafeRunSync()
    val (embed, shutdown) = provider match {
      case "openai" =>
        OpenAIEmbedModel.create(OpenAIEmbeddingInferenceModelConfig(model = model), env).allocated.unsafeRunSync()
      case "cohere" =>
        CohereEmbedModel.create(CohereEmbeddingInferenceModelConfig(model = model), env).allocated.unsafeRunSync()
      case other => throw Exception("nope")
    }
    val result = embed.encode(Raw, List(text)).compile.toList.unsafeRunSync()
    shutdown.unsafeRunSync()
    result.head
  }
}
