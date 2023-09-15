package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, IndexFixture, TestIndexMapping}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SearchRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import ai.nixiesearch.util.HttpTest.*
  import SearchRoute.*

  val mapping = TestIndexMapping()
  val index = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "search over lucene query syntax" in new Index {
    val route = SearchRoute(registry)
    val response =
      send[String, SearchResponse](route.routes, "http://localhost/test/_search?q=pajama", None, Method.GET)
    response.hits.size shouldBe 1
  }

  it should "search over lucene query syntax with empty query" in new Index {
    val route = SearchRoute(registry)
    val response =
      send[String, SearchResponse](route.routes, "http://localhost/test/_search", None, Method.GET)
    response.hits.size shouldBe 3
  }

  it should "search over dsl with empty query" in new Index {
    val route = SearchRoute(registry)
    val response =
      send[SearchRequest, SearchResponse](
        route.routes,
        "http://localhost/test/_search",
        None,
        Method.POST
      )
    response.hits.size shouldBe 3
  }

  it should "search over dsl" in new Index {
    val route   = SearchRoute(registry)
    val request = SearchRequest(MatchQuery("title", "pajama"), size = 10)
    val response =
      send[SearchRequest, SearchResponse](
        route.routes,
        "http://localhost/test/_search",
        Some(request),
        Method.POST
      )
    response.hits.size shouldBe 1
  }

}
