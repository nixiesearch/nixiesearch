package ai.nixiesearch.api

import cats.data.NonEmptyList
import cats.effect.IO
import org.http4s.{Header, Headers, HttpRoutes, Method, Request, Response, Status, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.http4s.dsl.io.GET
import org.typelevel.ci.CIString

class CORSTest extends AnyFlatSpec with Matchers {

  it should "add CORS header for existing indices" in {
    val result = getCorsHeaders(HealthRoute().routes, "http://localhost:8080/health", "http://somehost.com")
    result shouldBe Some(NonEmptyList.one(Header.Raw(CIString("Access-Control-Allow-Origin"), "*")))
  }

  it should "add CORS header for docs for 404 pages" in {
    val result = getCorsHeaders(HealthRoute().routes, "http://localhost:8080/health404", "http://somehost.com")
    result shouldBe Some(NonEmptyList.one(Header.Raw(CIString("Access-Control-Allow-Origin"), "*")))
  }

  it should "add CORS header for errors" in {
    val route = HttpRoutes.of[IO] { case GET @ _ =>
      IO(Response[IO](Status.BadRequest))
    }
    val result = getCorsHeaders(route, "http://localhost:8080/aaa", "http://somehost.com")
    result shouldBe Some(NonEmptyList.one(Header.Raw(CIString("Access-Control-Allow-Origin"), "*")))
  }

  def getCorsHeaders(routes: HttpRoutes[IO], endpoint: String, origin: String) = {
    val route = API.wrapMiddleware(routes)
    val request = Request(
      uri = Uri.unsafeFromString(endpoint),
      method = Method.GET,
      headers = Headers(Header.Raw(CIString("Origin"), origin))
    )
    val response = route(request).unsafeRunSync()
    val cors     = response.headers.get(CIString("Access-Control-Allow-Origin"))
    cors
  }

}
