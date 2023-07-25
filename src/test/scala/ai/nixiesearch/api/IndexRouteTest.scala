package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IntField, TextField}
import ai.nixiesearch.util.{TestIndexBuilder, TestIndexMapping}
import org.http4s.{Entity, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import scodec.bits.ByteVector
import io.circe.syntax.*
import cats.effect.IO

class IndexRouteTest extends AnyFlatSpec with Matchers {
  lazy val route = IndexRoute(Map("test" -> TestIndexBuilder(TestIndexMapping())))
  import IndexRoute._
  import ai.nixiesearch.util.IndexResponseEquality._

  it should "return index mapping on GET" in {
    val response =
      route.routes(Request(uri = Uri.unsafeFromString("http://localhost/test/_mapping"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
  }

  it should "fail on 404" in {
    val response =
      route.routes(Request(uri = Uri.unsafeFromString("http://localhost/nope/_mapping"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(404)
  }

  it should "accept docs" in {
    val doc = Document(List(TextField("id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
    val request = Request(
      uri = Uri.unsafeFromString("http://localhost/test/_index"),
      entity = Entity.strict(ByteVector(doc.asJson.noSpaces.getBytes())),
      method = Method.PUT
    )
    val response = for {
      httpResponseOption <- route.routes(request).value
      httpResponse       <- IO.fromOption(httpResponseOption)(new Exception("got no response"))
      decoded            <- httpResponse.as[IndexResponse]
    } yield {
      decoded
    }
    response.unsafeRunSync() === IndexResponse("created")
  }

  it should "flush index" in {
    val request = Request(
      uri = Uri.unsafeFromString("http://localhost/test/_flush"),
      method = Method.POST
    )
    val response = for {
      httpResponseOption <- route.routes(request).value
      httpResponse       <- IO.fromOption(httpResponseOption)(new Exception("got no response"))
      decoded            <- httpResponse.as[IndexResponse]
    } yield {
      decoded
    }
    response.unsafeRunSync() === IndexResponse("flushed")
  }

}
