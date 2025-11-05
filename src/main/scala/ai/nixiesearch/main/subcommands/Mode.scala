package ai.nixiesearch.main.subcommands

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs
import ai.nixiesearch.util.EnvVars
import cats.effect.IO

trait Mode[T <: CliArgs] extends Logging {
  def run(args: T, env: EnvVars): IO[Unit]
}
