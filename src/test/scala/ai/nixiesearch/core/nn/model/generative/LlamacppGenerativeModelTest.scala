package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.util.TestInferenceConfig
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import fs2.Stream

import java.nio.file.Paths

class LlamacppGenerativeModelTest extends AnyFlatSpec with Matchers {
  lazy val fileCache = ModelFileCache(Paths.get("/tmp/nixiesearch/"))

  it should "load, generate text, unload" in {
    val (cache, shutdownHandle) =
      GenerativeModelDict
        .create(TestInferenceConfig.full().completion, fileCache)
        .allocated
        .unsafeRunSync()

    val result = cache.generate(ModelRef("qwen2"), "Say a dad joke about chicken", 256).compile.toList.unsafeRunSync()
    val short  = cache.generate(ModelRef("qwen2"), "Say a dad joke about chicken", 10).compile.toList.unsafeRunSync()
    shutdownHandle.unsafeRunSync()
    val expected = "Why did the chicken cross the road? To get to the chicken coop!"
    result.mkString("") shouldBe expected
    short.mkString("") shouldBe "Why did the chicken cross the road? To get"
  }
}
