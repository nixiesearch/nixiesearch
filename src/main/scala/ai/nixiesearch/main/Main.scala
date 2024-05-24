package ai.nixiesearch.main

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.Loglevel
import ai.nixiesearch.main.subcommands.{IndexMode, SearchMode, StandaloneMode}
import cats.effect.{ExitCode, IO, IOApp}
import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    _    <- info("Staring Nixiesearch")
    opts <- CliConfig.load(args)
    _    <- changeLogbackLevel(opts.loglevel)
    _ <- opts match {
      case s: StandaloneArgs => StandaloneMode.run(s)
      case s: SearchArgs     => SearchMode.run(s)
      case s: IndexArgs      => IndexMode.run(s)
    }
  } yield {
    ExitCode.Success
  }

  def changeLogbackLevel(level: Loglevel): IO[Unit] = IO {
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val logger        = loggerContext.exists(org.slf4j.Logger.ROOT_LOGGER_NAME)
    val newLevel = level match {
      case Loglevel.DEBUG => Level.toLevel("DEBUG", null)
      case Loglevel.INFO  => Level.toLevel("INFO", null)
      case Loglevel.WARN  => Level.toLevel("WARN", null)
      case Loglevel.ERROR => Level.toLevel("ERROR", null)
    }
    logger.warn(s"Setting loglevel to $level")
    logger.setLevel(newLevel)
  }
}
