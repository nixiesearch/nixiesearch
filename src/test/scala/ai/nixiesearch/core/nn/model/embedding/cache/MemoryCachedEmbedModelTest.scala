package ai.nixiesearch.core.nn.model.embedding.cache

import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Raw
import ai.nixiesearch.core.nn.model.embedding.cache.MemoryCachedEmbedModelTest.CountingEmbedModel
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import cats.effect.unsafe.implicits.global

import scala.util.Random

class MemoryCachedEmbedModelTest extends AnyFlatSpec with Matchers {
  it should "always cache same" in {
    val base   = CountingEmbedModel()
    val cached = MemoryCachedEmbedModel.createUnsafe(base, MemoryCacheConfig())
    val pass1  = cached.encode(Raw, List("test")).compile.toList.unsafeRunSync().head
    val pass2  = cached.encode(Raw, List("test")).compile.toList.unsafeRunSync().head
    pass1.sameElements(pass2)
    base.count shouldBe 1
  }

  it should "compute embeds incrementally" in {
    val base   = CountingEmbedModel()
    val cached = MemoryCachedEmbedModel.createUnsafe(base, MemoryCacheConfig())
    val pass1  = cached.encode(Raw, List("a", "b")).compile.toList.unsafeRunSync()
    val pass2  = cached.encode(Raw, List("b", "c")).compile.toList.unsafeRunSync()
    pass1(1) sameElements pass2(0)
    base.count shouldBe 3
  }
}

object MemoryCachedEmbedModelTest {
  case class CountingEmbedModel(var count: Int = 0) extends EmbedModel {
    val batchSize = 1
    val model     = "dummy"
    val provider  = "dummy"

    override def encode(task: EmbedModel.TaskType, docs: List[String]): Stream[IO, Array[Float]] =
      Stream.emits(docs).map(_ => Array(Random.nextFloat())).evalTap(_ => IO { count += 1 })
  }
}
