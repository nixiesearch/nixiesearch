package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.util.{LocalNixie, TestIndexMapping, TestInferenceConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SearcherTest extends AnyFlatSpec with Matchers {
  it should "fail if index is missing" in {
    val (cluster, shutdown) = LocalNixie.create(TestIndexMapping(), TestInferenceConfig()).allocated.unsafeRunSync()
    a[BackendError] shouldBe thrownBy {
      cluster.searcher.search(SearchRequest(MatchAllQuery())).unsafeRunSync()
    }
    shutdown.unsafeRunSync()
  }
}
