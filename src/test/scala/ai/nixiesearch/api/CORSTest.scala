package ai.nixiesearch.api

import cats.data.NonEmptyList
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.typelevel.ci.CIString

class CORSTest extends AnyFlatSpec with Matchers {

  it should "accept docs for existing indices" in {
    val route = API.wrapMiddleware(HealthRoute().routes)
    val request = Request(
      uri = Uri.unsafeFromString("http://localhost:8080/health"),
      method = Method.GET,
      headers = Headers(Header.Raw(CIString("Origin"), "http://somehost.com"))
    )
    val response = route(request).value.unsafeRunSync().get
    val cors     = response.headers.get(CIString("Access-Control-Allow-Origin"))
    cors shouldBe Some(NonEmptyList.one(Header.Raw(CIString("Access-Control-Allow-Origin"), "*")))
  }

}
