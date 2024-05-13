package ai.nixiesearch.index.sync

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.index.Searcher
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class LocalIndexMemTest extends LocalIndexSuite {
  override val config = StoreConfig.LocalStoreConfig(LocalStoreLocation.MemoryLocation())
}
