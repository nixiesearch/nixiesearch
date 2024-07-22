package ai.nixiesearch.core.nn.model

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.nn.model.ModelCache.CacheKey
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.apache.commons.io.IOUtils

import java.nio.file.Files
import scala.util.Random

class ModelCacheTest extends AnyFlatSpec with Matchers {
  it should "fail on get empty" in {
    val cache = ModelCache.create(CacheConfig()).unsafeRunSync()
    a[BackendError] shouldBe thrownBy {
      cache.get(CacheKey("ns", "model", "file.onnx")).unsafeRunSync()
    }
  }

  it should "write and read" in {
    val cache      = ModelCache.create(CacheConfig()).unsafeRunSync()
    val randomFile = math.abs(Random.nextInt(1000000)).toString + ".onnx"
    val content    = Random.nextString(1024)
    val key        = CacheKey("test", "test", randomFile)
    cache.put(key, Stream(content.getBytes()*)).unsafeRunSync()
    val cachedFile    = cache.get(key).unsafeRunSync()
    val cachedContent = Files.readString(cachedFile)
    cachedContent shouldBe content
  }
}
