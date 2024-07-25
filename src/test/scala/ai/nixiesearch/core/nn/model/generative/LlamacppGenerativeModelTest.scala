package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.mapping.RAGConfig.PromptTemplate.{Llama3Template, Qwen2Template}
import ai.nixiesearch.config.mapping.RAGConfig.RAGModelConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.core.nn.model.generative.GenerativeModelDict.ModelId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Paths

class LlamacppGenerativeModelTest extends AnyFlatSpec with Matchers {
  val handle         = HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF", Some("qwen2-0_5b-instruct-q4_0.gguf"))
  lazy val fileCache = ModelFileCache(Paths.get("/tmp/"))

  it should "load, generate text, unload" in {
    val (cache, shutdownHandle) =
      GenerativeModelDict
        .create(List(RAGModelConfig(handle, Qwen2Template, "qwen")), fileCache)
        .allocated
        .unsafeRunSync()
    val result = cache.generate(ModelId("qwen"), "knock knock! who is there?").compile.toList.unsafeRunSync()
    shutdownHandle.unsafeRunSync()
    val expected =
      "I'm an AI, so I don't have a physical body and can't make knock knock. But you can reach out and I'll do my best to answer your questions!"
    result.mkString("") shouldBe expected
  }
}
