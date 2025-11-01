package ai.nixiesearch.api

import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SearchRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import ai.nixiesearch.util.HttpTest.*
  import SearchRoute.*

  val mapping = TestIndexMapping().copy(alias = List(Alias("test_alias")))
  val docs    = List(
    Document(List(IdField("_id", "1"), TextField("title", "red dress"))),
    Document(List(IdField("_id", "2"), TextField("title", "white dress"))),
    Document(List(IdField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "search over dsl with empty query" in withIndex { index =>
    {
      val route = SearchRoute(index.searcher)
      import route.given
      val response =
        send[SearchRequest, SearchResponse](
          route.routes,
          "http://localhost/test/_search",
          None,
          Method.POST
        )
      response.hits.size shouldBe 3
    }
  }

  it should "search over dsl with empty query and alias" in withIndex { index =>
    {
      val route = SearchRoute(index.searcher)
      import route.given
      val response =
        send[SearchRequest, SearchResponse](
          route.routes,
          "http://localhost/test_alias/_search",
          None,
          Method.POST
        )
      response.hits.size shouldBe 3
    }
  }

  it should "search over dsl" in withIndex { index =>
    {
      val route = SearchRoute(index.searcher)
      import route.given
      val request  = SearchRequest(MatchQuery("title", "pajama"), size = 10)
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

  it should "fail on non-existent field with 4xx code" in withIndex { index =>
    {
      val route   = SearchRoute(index.searcher)
      val request = SearchRequest(MatchQuery("title_404", "pajama"))
      a[UserError] should be thrownBy {
        sendRaw[SearchRequest](
          route.routes,
          "http://localhost/test/_search",
          Some(request),
          Method.POST
        )
      }
    }
  }

  it should "fail on non-text field with 4xx code" in withIndex { index =>
    {
      val route   = SearchRoute(index.searcher)
      val request = SearchRequest(MatchQuery("price", "10"))

      a[UserError] should be thrownBy {
        sendRaw[SearchRequest](
          route.routes,
          "http://localhost/test/_search",
          Some(request),
          Method.POST
        )
      }
    }
  }

}
