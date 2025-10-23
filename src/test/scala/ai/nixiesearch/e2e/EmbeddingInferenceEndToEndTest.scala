package ai.nixiesearch.e2e

import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.{Query, Raw}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.onnx.OnnxModelFile
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Tables.Table
import cats.effect.unsafe.implicits.global
import org.scalatest.prop.TableDrivenPropertyChecks.forAll

import java.nio.file.Paths

class EmbeddingInferenceEndToEndTest extends AnyFlatSpec with Matchers {
  val models = Table(
    ("name", "dims", "file"),
    ("intfloat/e5-small-v2", 384, None),
    ("intfloat/e5-small", 384, None),
    ("intfloat/multilingual-e5-small", 384, None),
    ("intfloat/multilingual-e5-base", 768, None),
    ("sentence-transformers/all-MiniLM-L6-v2", 384, None),
    ("Alibaba-NLP/gte-base-en-v1.5", 768, None),
    ("Alibaba-NLP/gte-modernbert-base", 768, None),
    ("Snowflake/snowflake-arctic-embed-m-v2.0", 768, None),
    ("BAAI/bge-small-en-v1.5", 384, None),
    ("BAAI/bge-m3", 1024, None),
    ("WhereIsAI/UAE-Large-V1", 1024, None),
    ("mixedbread-ai/mxbai-embed-large-v1", 1024, None),
    ("BAAI/bge-base-en", 768, None),
    ("jinaai/jina-embeddings-v3", 1024, None),
    ("onnx-community/Qwen3-Embedding-0.6B-ONNX", 1024, Some("onnx/model_uint8.onnx"))
  )

  it should "load the model and embed" taggedAs (EndToEnd.Embeddings) in {
    forAll(models) { (model, dims, maybeFile) =>
      {
        val result = EmbeddingInferenceEndToEndTest.embed(model, maybeFile, "test")
        result.length shouldBe dims
      }
    }
  }
}

object EmbeddingInferenceEndToEndTest {
  def embed(model: String, file: Option[String], text: String): Array[Float] = {
    val parts                      = model.split("/")
    val handle                     = HuggingFaceHandle(parts(0), parts(1))
    val config                     = OnnxEmbeddingInferenceModelConfig(model = handle, file = file)
    val (embedder, shutdownHandle) = OnnxEmbedModel
      .create(handle, config, ModelFileCache(Paths.get("/tmp/nixiesearch/")))
      .allocated
      .unsafeRunSync()
    val result = embedder.encode(Raw, List(text)).compile.toList.unsafeRunSync()
    shutdownHandle.unsafeRunSync()
    result(0)
  }
}
