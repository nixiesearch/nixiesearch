package ai.nixiesearch.api

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IndexRouteAdminTest extends AnyFlatSpec with Matchers with SearchTest {
  import IndexRoute.*
  import ai.nixiesearch.util.HttpTest.*
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10))),
    Document(List(TextField("_id", "2"), TextField("title", "foo aaa"), IntField("price", 10))),
    Document(List(TextField("_id", "3"), TextField("title", "foo bbb"), IntField("price", 11)))
  )
  val mapping            = TestIndexMapping()
  override val inference = TestInferenceConfig.empty()

  it should "handle flush" in withIndex { store =>
    {
      val response2 =
        send[Unit, EmptyResponse](
          IndexRoute(store.indexer).routes,
          "http://localhost/test/_flush",
          None,
          Method.POST
        )
      response2.status shouldBe "ok"
    }
  }

  it should "handle merge with no body" in withIndex { store =>
    {
      val response2 =
        send[Unit, EmptyResponse](
          IndexRoute(store.indexer).routes,
          "http://localhost/test/_merge",
          None,
          Method.POST
        )
      response2.status shouldBe "ok"
    }
  }

}
