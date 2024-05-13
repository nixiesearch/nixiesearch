package ai.nixiesearch.main

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.fileConverter
import cats.effect.IO
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter, throwError}

import java.io.File
import scala.util.{Failure, Success, Try}

case class CliConfig(arguments: List[String]) extends ScallopConf(arguments) with Logging {
  import CliConfig.*

  trait ConfigOption { this: Subcommand =>
    val config =
      opt[File](name = "config", short = 'c', descr = "Path to a config file", required = false)(fileConverter)
  }

  object standalone extends Subcommand("standalone") with ConfigOption
  object index      extends Subcommand("index") with ConfigOption
  object search     extends Subcommand("search") with ConfigOption
  addSubcommand(standalone)
  addSubcommand(index)
  addSubcommand(search)

  override protected def onError(e: Throwable): Unit = e match {
    case r: ScallopResult if !throwError.value =>
      r match {
        case Help("") =>
          logger.info("\n" + builder.getFullHelpString())
        case Help(subname) =>
          logger.info("\n" + builder.findSubbuilder(subname).get.getFullHelpString())
        case Version =>
          "\n" + getVersionString().foreach(logger.info)
        case ScallopException(message) => errorMessageHandler(message)
        // following should never match, but just in case
        case other: ScallopException => errorMessageHandler(other.getMessage)
      }
    case e => throw e
  }
}

object CliConfig extends Logging {
  sealed trait CliArgs
  object CliArgs {
    case class StandaloneArgs(config: Option[File]) extends CliArgs
    case class IndexArgs(config: Option[File])      extends CliArgs
    case class SearchArgs(config: Option[File])     extends CliArgs
  }

  def load(args: List[String]): IO[CliArgs] = for {
    parser <- IO(CliConfig(args))
    _      <- IO(parser.verify())
    opts <- parser.subcommand match {
      case Some(parser.standalone) =>
        for {
          config <- parseOption(parser.standalone.config)
        } yield {
          StandaloneArgs(config)
        }
      case Some(parser.index) =>
        for {
          config <- parseOption(parser.index.config)
        } yield {
          IndexArgs(config)
        }
      case Some(parser.search) =>
        for {
          config <- parseOption(parser.search.config)
        } yield {
          SearchArgs(config)
        }
      case Some(other) =>
        IO.raiseError(new Exception(s"Subcommand $other is not supported. Try standalone/search/index."))
      case None => IO.raiseError(new Exception("No command given. If unsure, try 'nixiesearch standalone'"))
    }
  } yield {
    opts
  }

  def parse[T](option: ScallopOption[T]): IO[T] = {
    Try(option.toOption) match {
      case Success(Some(value)) => IO.pure(value)
      case Success(None)        => IO.raiseError(new Exception(s"missing required option ${option.name}"))
      case Failure(ex)          => IO.raiseError(ex)
    }
  }

  def parseOption[T](option: ScallopOption[T]): IO[Option[T]] = {
    Try(option.toOption) match {
      case Success(value) => IO.pure(value)
      case Failure(ex)    => IO.raiseError(ex)
    }
  }

  given fileConverter: ValueConverter[File] = singleArgConverter(string => {
    val file = if (string.startsWith("/")) {
      new File(string)
    } else {
      val prefix = System.getProperty("user.dir")
      new File(s"$prefix/$string")
    }
    if (file.exists()) {
      file
    } else {
      Option(file.getParentFile) match {
        case Some(parent) if parent.exists() && parent.isDirectory =>
          val other = Option(parent.listFiles()).map(_.map(_.getName).mkString("\n", "\n", "\n"))
          throw new Exception(s"$file: file does not exist. Perhaps you've meant: $other")
        case _ => throw new Exception(s"$file: file does not exist (and we cannot list parent directory)")
      }

    }
  })
}
