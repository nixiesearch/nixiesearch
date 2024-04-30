package ai.nixiesearch.index

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Files

class IndexTest extends AnyFlatSpec with Matchers {
  it should "create mem index" in {
    noException should be thrownBy {
      Index
        .openOrCreate(mapping = TestIndexMapping(), store = MemoryStoreConfig(), cache = CacheConfig())
        .unsafeRunSync()
    }
  }

  it should "create on-disk index of empty" in {
    val tmpdir = Files.createTempDirectory("nixiesearch_tmp_")
    noException should be thrownBy {
      Index
        .openOrCreate(
          mapping = TestIndexMapping(),
          store = LocalStoreConfig(LocalStoreUrl(tmpdir.toString)),
          cache = CacheConfig()
        )
        .unsafeRunSync()
    }
  }

  it should "open existing on-disk index" in {
    val tmpdir = Files.createTempDirectory("nixiesearch_tmp_")
    noException should be thrownBy {
      val index1 = Index
        .openOrCreate(
          mapping = TestIndexMapping(),
          store = LocalStoreConfig(LocalStoreUrl(tmpdir.toString)),
          cache = CacheConfig()
        )
        .unsafeRunSync()
      index1.dir.close()
      val index2 = Index
        .openOrCreate(
          mapping = TestIndexMapping(),
          store = LocalStoreConfig(LocalStoreUrl(tmpdir.toString)),
          cache = CacheConfig()
        )
        .unsafeRunSync()
    }
  }
}
