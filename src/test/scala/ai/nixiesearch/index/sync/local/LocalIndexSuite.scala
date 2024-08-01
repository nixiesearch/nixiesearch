package ai.nixiesearch.index.sync

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.index.{Indexer, Searcher}
import ai.nixiesearch.util.{TestDocument, TestIndexMapping}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

trait LocalIndexSuite extends AnyFlatSpec with Matchers {
  def config: LocalStoreConfig

  it should "start with empty index" in {
    val (localIndex, localShutdown) = LocalIndex
      .create(TestIndexMapping(), config, CacheConfig())
      .allocated
      .unsafeRunSync()

    val (searcher, searcherShutdown) = Searcher.open(localIndex).allocated.unsafeRunSync()
    a[BackendError] shouldBe thrownBy {
      searcher.search(SearchRequest(query = MatchAllQuery())).unsafeRunSync()

    }
    searcherShutdown.unsafeRunSync()
    localShutdown.unsafeRunSync()
  }

  it should "write docs and search over them" in {
    val (localIndex, localShutdown) = LocalIndex
      .create(TestIndexMapping(), config, CacheConfig())
      .allocated
      .unsafeRunSync()

    val (writer, writerShutdown) = Indexer.open(localIndex).allocated.unsafeRunSync()
    writer.addDocuments(List(TestDocument())).unsafeRunSync()
    writer.flush().unsafeRunSync()

    val (searcher, searcherShutdown) = Searcher.open(localIndex).allocated.unsafeRunSync()
    val response                     = searcher.search(SearchRequest(query = MatchAllQuery())).unsafeRunSync()
    response.hits.size shouldBe 1
    writerShutdown.unsafeRunSync()
    searcherShutdown.unsafeRunSync()
    localShutdown.unsafeRunSync()

  }

}
