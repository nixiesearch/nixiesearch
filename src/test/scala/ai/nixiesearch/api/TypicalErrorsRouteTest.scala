package ai.nixiesearch.api

import cats.effect.IO
import org.http4s.{Method, Request, Status, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class TypicalErrorsRouteTest extends AnyFlatSpec with Matchers {
  lazy val routes = TypicalErrorsRoute(List("movies")).routes

  it should "throw 404 on missing index" in {
    val response = fetch(Method.POST, "http://localhost/movies2/_search")
    response shouldBe Some(Status.NotFound)
  }

  it should "throw BadRequest on GET index" in {
    val response = fetch(Method.GET, "http://localhost/movies/_search")
    response shouldBe Some(Status.BadRequest)
  }

  it should "throw BadRequest on wrong rest verb" in {
    val response = fetch(Method.POST, "http://localhost/movies/_yay")
    response shouldBe Some(Status.BadRequest)
  }

  def fetch(method: Method, uri: String): Option[Status] = {
    routes
      .run(Request[IO](method = method, uri = Uri.unsafeFromString(uri)))
      .map(_.status)
      .value
      .unsafeRunSync()
  }
}
