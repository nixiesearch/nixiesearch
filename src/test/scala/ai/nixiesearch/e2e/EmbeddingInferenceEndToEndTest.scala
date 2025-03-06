package ai.nixiesearch.e2e

import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
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
    // ("Alibaba-NLP/gte-Qwen2-1.5B-instruct", 1), // no onnx
    ("Alibaba-NLP/gte-base-en-v1.5", 768),
    ("Alibaba-NLP/gte-modernbert-base", 768),
    // ("openbmb/MiniCPM-Embedding", 1), // no onnx
    // ("NovaSearch/stella_en_400M_v5", 1), // no onnx
    // ("infly/inf-retriever-v1-1.5b", 1), // no onnx
    ("Snowflake/snowflake-arctic-embed-m-v2.0", 768),
    ("BAAI/bge-small-en-v1.5", 384),
    ("WhereIsAI/UAE-Large-V1", 1024),
    ("mixedbread-ai/mxbai-embed-large-v1", 1024),
    // ("avsolatorio/GIST-large-Embedding-v0", 1), // no onnx
    ("BAAI/bge-base-en", 768),
    // ("jxm/cde-small-v2", 1), // no onnx
    ("jinaai/jina-embeddings-v3", 1024)
    // ("llmrails/ember-v1", 1), // no onnx
    // ("ibm-granite/granite-embedding-125m-english", 1) // no onnx
  )

  it should "load the model and embed" in {
    forAll(models) { (model, dims) =>
      {
        val parts  = model.split("/")
        val handle = HuggingFaceHandle(parts(0), parts(1))
        val config = OnnxEmbeddingInferenceModelConfig(model = handle)
        val (embedder, shutdownHandle) = EmbedModelDict
          .createHuggingface(handle, config, ModelFileCache(Paths.get("/tmp/models/")))
          .allocated
          .unsafeRunSync()
        val result = embedder.encode(List("query: test")).unsafeRunSync()
        result.length shouldBe 1
        result(0).length shouldBe dims
        shutdownHandle.unsafeRunSync()
      }
    }
  }
}
