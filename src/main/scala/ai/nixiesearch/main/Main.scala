package ai.nixiesearch.main

import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs, TraceArgs}
import ai.nixiesearch.main.CliConfig.{CliArgs, Loglevel}
import ai.nixiesearch.main.subcommands.{IndexMode, SearchMode, StandaloneMode, TraceMode}
import ai.nixiesearch.util.{EnvVars, GPUUtils}
import ai.nixiesearch.util.analytics.AnalyticsReporter
import cats.effect.std.Env
import cats.effect.{ExitCode, IO, IOApp}
import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory
import fs2.Stream

object Main extends IOApp with Logging {
  case class ArgsEnv(args: CliArgs, env: EnvVars)

  override def run(args: List[String]): IO[ExitCode] = for {
    argsEnv <- init(args)
    _       <- argsEnv.args match {
      case s: StandaloneArgs => StandaloneMode.run(s, argsEnv.env)
      case s: SearchArgs     => SearchMode.run(s, argsEnv.env)
      case s: IndexArgs      => IndexMode.run(s, argsEnv.env)
      case s: TraceArgs      => TraceMode.run(s, argsEnv.env)
    }
  } yield {
    ExitCode.Success
  }

  def init(args: List[String]): IO[ArgsEnv] = for {
    _    <- info(s"Starting Nixiesearch: ${Logo.version}")
    _    <- IO(System.setProperty("ai.djl.offline", "true")) // too slow
    args <- CliConfig.load(args)
    _    <- changeLogbackLevel(args.loglevel)
    env  <- EnvVars.load()
    _    <- gpuChecks()
  } yield {
    ArgsEnv(args, env)
  }

  def changeLogbackLevel(level: Loglevel): IO[Unit] = IO {
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val logger        = loggerContext.exists(org.slf4j.Logger.ROOT_LOGGER_NAME)
    val newLevel      = level match {
      case Loglevel.DEBUG => Level.toLevel("DEBUG", null)
      case Loglevel.INFO  => Level.toLevel("INFO", null)
      case Loglevel.WARN  => Level.toLevel("WARN", null)
      case Loglevel.ERROR => Level.toLevel("ERROR", null)
    }
    logger.warn(s"Setting loglevel to $level")
    logger.setLevel(newLevel)
  }

  def gpuChecks(): IO[Unit] = for {
    _ <- IO(GPUUtils.isGPUBuild()).flatMap {
      case false => info("Nixiesearch CPU inference build. GPU inference not supported")
      case true  =>
        info("ONNX CUDA EP Found: GPU Build") *> Stream
          .evalSeq(GPUUtils.listDevices())
          .evalMap(device => info(s"GPU ${device.id}: ${device.model}"))
          .compile
          .drain
    }
  } yield {}
}
