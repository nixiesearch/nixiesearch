package ai.nixiesearch.api

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IndexRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import IndexModifyRoute.*
  import ai.nixiesearch.util.HttpTest.*
  val docs               = Nil
  val mapping            = TestIndexMapping()
  override val inference = TestInferenceConfig.empty()

  it should "accept docs for existing indices" in withIndex { store =>
    {
      val doc      = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      val response =
        send[Document, IndexResponse](
          IndexModifyRoute(store.indexer).routes,
          "http://localhost/test/_index",
          Some(doc),
          Method.PUT
        )
      response.status shouldBe "ok"
    }
  }

  it should "not accept docs for new indices" in withIndex { store =>
    {
      val doc = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      an[Exception] should be thrownBy {
        send[Document, IndexResponse](
          IndexModifyRoute(store.indexer).routes,
          "http://localhost/test2/_index",
          Some(doc),
          Method.PUT
        )
      }
    }
  }

}
