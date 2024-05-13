package ai.nixiesearch.index.sync.master

import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.index.sync.MasterIndex
import ai.nixiesearch.util.TestIndexMapping
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait MasterIndexSuite extends AnyFlatSpec with Matchers {
  def config: DistributedStoreConfig
  it should "start from empty" in {
    val (emptyIndex, indexClose) =
      MasterIndex.create(TestIndexMapping(), config, CacheConfig()).allocated.unsafeRunSync()
    emptyIndex.sync().unsafeRunSync()
    val mf = emptyIndex.replica.readManifest().unsafeRunSync()
    mf.isDefined shouldBe true
    indexClose.unsafeRunSync()
  }
}
