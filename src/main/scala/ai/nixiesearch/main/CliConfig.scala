package ai.nixiesearch.main

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.URL
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSource.*
import ai.nixiesearch.main.CliConfig.CliArgs.*
import ai.nixiesearch.main.CliConfig.Loglevel.INFO
import cats.effect.IO
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version}
import org.rogach.scallop.*

import java.io.File
import scala.util.{Failure, Success, Try}

case class CliConfig(arguments: List[String]) extends ScallopConf(arguments) with Logging {
  import CliConfig.*
  import ai.nixiesearch.main.args.implicits.given

  trait ConfigOption { this: Subcommand =>
    val config =
      opt[File](name = "config", short = 'c', descr = "Path to a config file", required = false)
  }

  trait LoglevelOption { this: Subcommand =>
    val loglevel = opt[Loglevel](
      name = "loglevel",
      short = 'l',
      descr = "Logging level: debug/info/warn/error, default=info",
      required = false,
      default = Some(INFO)
    )
  }

  object standalone extends Subcommand("standalone") with ConfigOption with LoglevelOption
  object index extends Subcommand("index") {
    object api extends Subcommand("api") with ConfigOption with LoglevelOption {
      val host =
        opt[Hostname](
          name = "host",
          required = false,
          descr = "iface to bind to, optional, default=0.0.0.0",
          default = Some(Hostname("0.0.0.0"))
        )
      val port =
        opt[Port](
          name = "port",
          required = false,
          default = Some(Port(8080)),
          descr = "port to bind to, optional, default=8080"
        )
    }
    object file extends Subcommand("file") with ConfigOption with LoglevelOption {
      val index = opt[String](name = "index", descr = "to which index to write to")
      val url   = opt[URL](name = "url", descr = "path to documents source")
      val recursive =
        opt[Boolean](
          name = "recursive",
          required = false,
          default = Some(false),
          descr = "recursive listing for directories, optional, default=false"
        )
      val endpoint =
        opt[String](
          name = "endpoint",
          required = false,
          default = None,
          descr = "custom S3 endpoint, optional, default=None"
        )
    }

    addSubcommand(api)
    addSubcommand(file)
  }
  object search extends Subcommand("search") with ConfigOption with LoglevelOption
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
  sealed trait CliArgs {
    def loglevel: Loglevel
  }
  object CliArgs {
    case class StandaloneArgs(config: File, loglevel: Loglevel = INFO)                 extends CliArgs
    case class IndexArgs(config: File, source: IndexSource, loglevel: Loglevel = INFO) extends CliArgs
    case class SearchArgs(config: File, loglevel: Loglevel = INFO)                     extends CliArgs

    enum IndexSource {
      case ApiIndexSource(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080)) extends IndexSource
      case FileIndexSource(url: URL, index: String, recursive: Boolean = false, endpoint: Option[String] = None)
          extends IndexSource
    }
  }

  enum Loglevel {
    case DEBUG extends Loglevel
    case INFO  extends Loglevel
    case WARN  extends Loglevel
    case ERROR extends Loglevel
  }

  def load(args: List[String]): IO[CliArgs] = for {
    parser <- IO(CliConfig(args))
    _      <- IO(parser.verify())
    opts <- parser.subcommand match {
      case Some(parser.standalone) =>
        for {
          config   <- parse(parser.standalone.config)
          loglevel <- parseOption(parser.standalone.loglevel)
        } yield {
          StandaloneArgs(config, loglevel.getOrElse(INFO))
        }
      case Some(parser.index) =>
        parser.index.subcommand match {
          case Some(parser.index.api) =>
            for {
              config   <- parse(parser.index.api.config)
              host     <- parseOption(parser.index.api.host)
              port     <- parseOption(parser.index.api.port)
              loglevel <- parseOption(parser.index.api.loglevel)
            } yield {
              IndexArgs(
                config = config,
                loglevel = loglevel.getOrElse(INFO),
                source = ApiIndexSource(host.getOrElse(Hostname("0.0.0.0")), port.getOrElse(Port(8080)))
              )
            }
          case Some(parser.index.file) =>
            for {
              config    <- parse(parser.index.file.config)
              url       <- parse(parser.index.file.url)
              index     <- parse(parser.index.file.index)
              recursive <- parseOption(parser.index.file.recursive)
              endpoint  <- parseOption(parser.index.file.endpoint)
              loglevel  <- parseOption(parser.index.file.loglevel)
            } yield {
              IndexArgs(
                config = config,
                loglevel = loglevel.getOrElse(INFO),
                source = FileIndexSource(url, index, recursive.getOrElse(false), endpoint)
              )
            }
          case Some(other) =>
            IO.raiseError(new Exception(s"Subcommand $other is not supported. Try indexer api."))
          case None =>
            IO.raiseError(new Exception("No command given. If unsure, try 'nixiesearch standalone'"))

        }
      case Some(parser.search) =>
        for {
          config   <- parse(parser.search.config)
          loglevel <- parseOption(parser.search.loglevel)
        } yield {
          SearchArgs(config, loglevel.getOrElse(INFO))
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
}
