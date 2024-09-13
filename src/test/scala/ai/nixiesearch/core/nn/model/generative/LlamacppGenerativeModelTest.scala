package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.util.TestInferenceConfig
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import fs2.Stream

import java.nio.file.Paths

class LlamacppGenerativeModelTest extends AnyFlatSpec with Matchers {
  lazy val fileCache = ModelFileCache(Paths.get("/tmp/"))

  it should "load, generate text, unload" in {
    val (cache, shutdownHandle) =
      GenerativeModelDict
        .create(TestInferenceConfig.full().completion, fileCache)
        .allocated
        .unsafeRunSync()

    val result = cache.generate(ModelRef("qwen2"), "knock knock! who is there?", 256).compile.toList.unsafeRunSync()
    val short  = cache.generate(ModelRef("qwen2"), "knock knock! who is there?", 10).compile.toList.unsafeRunSync()
    shutdownHandle.unsafeRunSync()
    val expected =
      "I'm an AI, so I don't have a physical body or a mind. I exist in the universe, so I can answer any questions you have."
    result.mkString("") shouldBe expected
    short.mkString("") shouldBe "I'm an AI, so I don't have"
  }
}
