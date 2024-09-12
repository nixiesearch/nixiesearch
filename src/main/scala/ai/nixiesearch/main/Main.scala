package ai.nixiesearch.main

import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.Loglevel
import ai.nixiesearch.main.subcommands.{IndexMode, SearchMode, StandaloneMode}
import ai.nixiesearch.util.GPUUtils
import cats.effect.{ExitCode, IO, IOApp}
import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory
import fs2.Stream

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    _      <- info("Staring Nixiesearch")
    opts   <- CliConfig.load(args)
    _      <- changeLogbackLevel(opts.loglevel)
    config <- Config.load(opts.config)
    _      <- gpuChecks(config)
    _ <- opts match {
      case s: StandaloneArgs => StandaloneMode.run(s, config)
      case s: SearchArgs     => SearchMode.run(s, config)
      case s: IndexArgs      => IndexMode.run(s, config)
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

  def gpuChecks(config: Config): IO[Unit] = for {
    _ <- GPUUtils.isGPUBuild().flatMap {
      case false => info("Nixiesearch CPU inference build. GPU inference not supported")
      case true =>
        info("ONNX CUDA EP Found: GPU Build") *> Stream
          .evalSeq(GPUUtils.listDevices())
          .evalMap(device => info(s"GPU ${device.id}: ${device.model}"))
          .compile
          .drain
    }
  } yield {}
}
