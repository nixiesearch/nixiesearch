package ai.nixiesearch.main

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.{CliArgs, Loglevel}
import ai.nixiesearch.main.subcommands.{IndexMode, SearchMode, StandaloneMode}
import ai.nixiesearch.util.{EnvVars, GPUUtils}
import ai.nixiesearch.util.PrintLogger
import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream

object Main extends IOApp with Logging {
  case class ArgsEnv(args: CliArgs, env: EnvVars)

  override def run(args: List[String]): IO[ExitCode] = for {
    argsEnv <- init(args)
    _       <- argsEnv.args match {
      case s: StandaloneArgs => StandaloneMode.run(s, argsEnv.env)
      case s: SearchArgs     => SearchMode.run(s, argsEnv.env)
      case s: IndexArgs      => IndexMode.run(s, argsEnv.env)
    }
  } yield {
    ExitCode.Success
  }

  def init(args: List[String]): IO[ArgsEnv] = for {
    _    <- info(s"Starting Nixiesearch: ${Logo.version}")
    _    <- IO(System.setProperty("ai.djl.offline", "true")) // too slow
    args <- CliConfig.load(args)
    _    <- changeLogLevel(args.loglevel)
    env  <- EnvVars.load()
    _    <- gpuChecks()
  } yield {
    ArgsEnv(args, env)
  }

  def changeLogLevel(level: Loglevel): IO[Unit] = IO {
    val newLevel = level match {
      case Loglevel.DEBUG => PrintLogger.LogLevel.DEBUG
      case Loglevel.INFO  => PrintLogger.LogLevel.INFO
      case Loglevel.WARN  => PrintLogger.LogLevel.WARN
      case Loglevel.ERROR => PrintLogger.LogLevel.ERROR
    }
    PrintLogger.setLogLevel(newLevel)
  } *> info(s"Log level set to $level")

  def gpuChecks(): IO[Unit] = for {
    _ <- IO(GPUUtils.isGPUBuild()).flatMap {
      case false => info("Nixiesearch CPU inference build. GPU inference not supported.")
      case true  =>
        info("ONNX CUDA EP found: GPU build.") *> Stream
          .evalSeq(GPUUtils.listDevices())
          .evalMap(device => info(s"GPU ${device.id}: ${device.model}"))
          .compile
          .drain
    }
  } yield {}
}
