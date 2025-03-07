package ai.nixiesearch.e2e

import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import cats.effect.unsafe.implicits.global
import org.scalatest.prop.TableDrivenPropertyChecks.forAll

import java.nio.file.Paths

class EmbeddingInferenceEndToEndTest extends AnyFlatSpec with Matchers {
  val models = Table(
    ("name", "dims"),
    ("intfloat/e5-small-v2", 384),
    ("intfloat/e5-small", 384),
    ("intfloat/multilingual-e5-small", 384),
    ("intfloat/multilingual-e5-base", 768),
    ("sentence-transformers/all-MiniLM-L6-v2", 384),
    ("Alibaba-NLP/gte-base-en-v1.5", 768),
    ("Alibaba-NLP/gte-modernbert-base", 768),
    ("Snowflake/snowflake-arctic-embed-m-v2.0", 768),
    ("BAAI/bge-small-en-v1.5", 384),
    ("WhereIsAI/UAE-Large-V1", 1024),
    ("mixedbread-ai/mxbai-embed-large-v1", 1024),
    ("BAAI/bge-base-en", 768),
    ("jinaai/jina-embeddings-v3", 1024)
  )

  it should "load the model and embed" taggedAs (EndToEnd.Embeddings) in {
    forAll(models) { (model, dims) =>
      {
        val result = EmbeddingInferenceEndToEndTest.embed(model, "query: test")
        result.length shouldBe dims
      }
    }
  }
}

object EmbeddingInferenceEndToEndTest {
  def embed(model: String, text: String): Array[Float] = {
    val parts  = model.split("/")
    val handle = HuggingFaceHandle(parts(0), parts(1))
    val config = OnnxEmbeddingInferenceModelConfig(model = handle)
    val (embedder, shutdownHandle) = EmbedModelDict
      .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch/")))
      .allocated
      .unsafeRunSync()
    val result = embedder.encode(List(text)).unsafeRunSync()
    shutdownHandle.unsafeRunSync()
    result(0)
  }
}
