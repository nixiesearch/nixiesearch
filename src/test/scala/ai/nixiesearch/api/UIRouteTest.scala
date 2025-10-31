package ai.nixiesearch.api

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class UIRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  val mapping = TestIndexMapping()
  val docs    = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "return index mapping" in withIndex { index =>
    {
      val route    = MainRoute()
      val response =
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/"))).value.unsafeRunSync()
      response.map(_.status.code) shouldBe Some(200)
    }
  }

}
