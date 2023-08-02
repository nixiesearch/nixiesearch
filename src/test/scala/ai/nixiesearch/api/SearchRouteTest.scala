package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, StoreFixture, TestIndexMapping}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SearchRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import ai.nixiesearch.util.HttpTest.*
  import SearchRoute.*

  val mapping = TestIndexMapping()
  val index = List(
    Document(List(TextField("id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("id", "3"), TextField("title", "red pajama")))
  )

  it should "search over lucene query syntax" in new Index {
    val route = SearchRoute(store)
    val response =
      send[String, SearchResponse](route.routes, "http://localhost/test/_search?q=pajama", None, Method.GET)
    response.hits.size shouldBe 1
  }

  it should "search over lucene query syntax with empty query" in new Index {
    val route = SearchRoute(store)
    val response =
      send[String, SearchResponse](route.routes, "http://localhost/test/_search", None, Method.GET)
    response.hits.size shouldBe 3
  }

  it should "search over dsl" in new Index {
    val route   = SearchRoute(store)
    val request = SearchRequest(MatchQuery("title", "pajama"), 10, List("id"))
    val response =
      send[SearchRequest, SearchResponse](
        route.routes,
        "http://localhost/test/_search?q=pajama",
        Some(request),
        Method.POST
      )
    response.hits.size shouldBe 1
  }

}
