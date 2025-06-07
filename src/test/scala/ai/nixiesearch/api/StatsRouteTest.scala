package ai.nixiesearch.api

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.index.IndexStats
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.http4s.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StatsRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import ai.nixiesearch.util.HttpTest.*

  val mapping = TestIndexMapping()
  val docs    = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "get index stats" in withIndex { index =>
    {
      val route    = StatsRoute(index.searcher)
      val response =
        sendRaw[IndexStats](
          route.routes,
          "http://localhost/test/_stats",
          None,
          Method.GET
        )
      response.isDefined shouldBe true
    }
  }
}
