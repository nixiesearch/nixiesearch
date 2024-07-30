package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.ErrorResponse
import ai.nixiesearch.core.Error.UserError
import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response, Status, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.dsl.io.*
import cats.effect.unsafe.implicits.global

class ErrorHandlerTest extends AnyFlatSpec with Matchers {
  lazy val routes = HttpRoutes.of[IO] {
    case GET -> Root / "exception" => IO.raiseError(new Exception("oops"))
    case GET -> Root / "user"      => IO.raiseError(new UserError("you bad"))
    case GET -> Root / "ok"        => IO(Response[IO](status = Status.Ok))
  }

  it should "handle exceptions" in {
    val app      = API.wrapMiddleware(routes)
    val response = app.run(Request[IO](uri = Uri.unsafeFromString("http://localhost/exception"))).unsafeRunSync()
    val error    = response.as[ErrorResponse].unsafeRunSync()
    error shouldBe ErrorResponse("oops")
    response.status shouldBe Status.InternalServerError
  }

  it should "handle user errors" in {
    val app      = API.wrapMiddleware(routes)
    val response = app.run(Request[IO](uri = Uri.unsafeFromString("http://localhost/user"))).unsafeRunSync()
    val error    = response.as[ErrorResponse].unsafeRunSync()
    error shouldBe ErrorResponse("you bad")
    response.status shouldBe Status.BadRequest
  }
}
