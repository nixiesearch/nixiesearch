package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.ErrorResponse
import ai.nixiesearch.core.Error.UserError
import cats.data.Kleisli
import cats.effect.IO
import org.http4s.Entity.Strict
import org.http4s.{Request, Response, Status}
import scodec.bits.ByteVector
import io.circe.syntax.*

object ErrorHandler {
  def handle(e: Throwable): Kleisli[IO, Request[IO], Response[IO]] = Kleisli { (req: Request[IO]) =>
    e match {
      case _: UserError =>
        IO.pure(
          Response[IO](
            status = Status.BadRequest,
            entity = Strict(ByteVector.view(ErrorResponse(e).asJson.noSpaces.getBytes))
          )
        )

      case _ =>
        IO.pure(
          Response[IO](
            status = Status.InternalServerError,
            entity = Strict(ByteVector.view(ErrorResponse(e).asJson.noSpaces.getBytes))
          )
        )
    }
  }
}
