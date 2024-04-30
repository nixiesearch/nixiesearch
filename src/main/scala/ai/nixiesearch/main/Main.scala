package ai.nixiesearch.main

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.subcommands.{IndexMode, SearchMode, StandaloneMode}
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    _    <- info("Staring Nixiesearch")
    opts <- CliConfig.load(args)
    _ <- opts match {
      case s: StandaloneArgs => StandaloneMode.run(s)
      case s: SearchArgs     => SearchMode.run(s)
      case s: IndexArgs      => IndexMode.run(s)
    }
  } yield {
    ExitCode.Success
  }
}
