package ai.nixiesearch.util

import ai.nixiesearch.core.Error.UserError
import cats.effect.IO
import cats.effect.std.Env

case class EnvVars(values: Map[String, String] = Map.empty) {
  def string(name: String) = values.get(name)
}

object EnvVars {
  def load(): IO[EnvVars] = Env[IO].entries.map(entries => EnvVars(entries.toMap))

}
