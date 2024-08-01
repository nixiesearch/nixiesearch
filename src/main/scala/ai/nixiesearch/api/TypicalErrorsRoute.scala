package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.ErrorResponse
import cats.effect.IO
import org.http4s.Entity.Strict
import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.dsl.io.*
import scodec.bits.ByteVector
import io.circe.syntax.*

case class TypicalErrorsRoute(indexes: List[String]) extends Route {
  val verbs = Set("_search", "_suggest")
  override def routes = HttpRoutes.of[IO] {
    case POST -> Root / index / handle if !indexes.contains(index) && verbs.contains(handle) =>
      error(Status.NotFound, s"Index '$index' not found. You can try others: [${indexes.mkString(", ")}]")
    case POST -> Root / index / handle if indexes.contains(index) && !verbs.contains(handle) =>
      error(Status.BadRequest, s"'$index'  only supports ${verbs} REST methods, but not the '$handle''")
    case other -> Root / index / handle if indexes.contains(index) && verbs.contains(handle) =>
      error(Status.BadRequest, s"Method '$index'/$handle expects HTTP POST, but got $other")
  }

  def error(code: Status, message: String): IO[Response[IO]] = IO.pure(
    Response(
      status = code,
      entity = Strict(ByteVector.view(ErrorResponse(message).asJson.noSpaces.getBytes))
    )
  )

}
