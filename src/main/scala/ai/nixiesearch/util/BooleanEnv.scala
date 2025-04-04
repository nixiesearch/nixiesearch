package ai.nixiesearch.util

import ai.nixiesearch.core.Error.UserError
import cats.effect.IO

object BooleanEnv {
  def parse(value: String): IO[Boolean] = value.trim.toLowerCase() match {
    case "y" | "yes" | "true" => IO.pure(true)
    case "n" | "no" | "false" => IO.pure(false)
    case other                => IO.raiseError(UserError(s"cannot parse boolean: got '$other'"))
  }
}
