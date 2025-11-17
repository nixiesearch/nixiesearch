package ai.nixiesearch.main

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.URL
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.ApiMode.Http
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, SearchArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.IndexSourceArgs.*
import ai.nixiesearch.main.CliConfig.{IndexSourceArgs, *}
import ai.nixiesearch.main.CliConfig.Loglevel.INFO
import ai.nixiesearch.source.SourceOffset
import ai.nixiesearch.source.SourceOffset.Latest
import ai.nixiesearch.util.Version
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version as ScallopVersion}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, throwError, given}

import scala.util.{Failure, Success, Try}

case class CliConfig(arguments: List[String]) extends ScallopConf(arguments) with Logging {
  import CliConfig.*
  import ai.nixiesearch.main.args.implicits.given

  trait ConfigOption { this: Subcommand =>
    val config =
      opt[URL](
        name = "config",
        short = 'c',
        descr = "URL of a config file. Can also be HTTP/S3 hosted.",
        required = false
      )
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
  object index      extends Subcommand("index") {
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
      val index     = opt[String](name = "index", descr = "to which index to write to")
      val url       = opt[URL](name = "url", descr = "path to documents source")
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
      val forceMerge = opt[Int](
        name = "force_merge",
        required = false,
        default = None,
        descr = "Run force-merge after indexing"
      )
    }
    object kafka extends Subcommand("kafka") with ConfigOption with LoglevelOption {
      val index   = opt[String](name = "index", descr = "to which index to write to")
      val brokers =
        opt[List[String]](name = "brokers", required = true, descr = "Kafka brokers endpoints, comma-separated list")
      val topic   = opt[String](name = "topic", required = true, descr = "Kafka topic name")
      val groupId = opt[String](
        name = "group_id",
        required = false,
        descr = "groupId identifier of consumer. default=nixiesearch",
        default = Some("nixiesearch")
      )
      val offset = opt[SourceOffset](
        name = "offset",
        required = false,
        default = Some(Latest),
        descr =
          "which topic offset to use for initial connection? earliest/latest/ts=<unixtime>/last=<offset> default=none (use committed offsets)"
      )
      val options = opt[Map[String, String]](
        name = "options",
        required = false,
        descr = "comma-separated list of kafka client custom options"
      )
    }

    addSubcommand(api)
    addSubcommand(file)
    addSubcommand(kafka)
  }
  object search extends Subcommand("search") with ConfigOption with LoglevelOption {
    val api = opt[ApiMode](
      name = "api",
      required = false,
      default = Some(ApiMode.Http),
      descr = "API serving mode: http or lambda"
    )
  }

  addSubcommand(standalone)
  addSubcommand(index)
  addSubcommand(search)

  version("Nixiesearch v:" + Version().getOrElse("unknown"))
  banner("""Usage: nixiesearch <subcommand> <options>
           |Options:
           |""".stripMargin)
  footer("\nFor all other tricks, consult the docs on https://nixiesearch.ai")

  override protected def onError(e: Throwable): Unit = e match {
    case r: ScallopResult if !throwError.value =>
      r match {
        case Help("") =>
          logger.info("\n" + builder.getFullHelpString())
        case Help(subname) =>
          logger.info("\n" + builder.findSubbuilder(subname).get.getFullHelpString())
        case ScallopVersion =>
          "\n" + getVersionString().foreach(logger.info)
        case e @ ScallopException(message) => throw e
        // following should never match, but just in case
        case other: ScallopException => throw other
      }
    case e => throw e
  }
}

object CliConfig extends Logging {
  enum CliArgs(val mode: String) {
    def loglevel: Loglevel

    case StandaloneArgs(config: URL, loglevel: Loglevel = INFO)                     extends CliArgs("standalone")
    case IndexArgs(config: URL, source: IndexSourceArgs, loglevel: Loglevel = INFO) extends CliArgs("index")
    case SearchArgs(config: URL, loglevel: Loglevel = INFO, api: ApiMode = Http)    extends CliArgs("search")
  }

  enum ApiMode {
    case Http   extends ApiMode
    case Lambda extends ApiMode
  }

  enum IndexSourceArgs {
    case ApiIndexSourceArgs(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080)) extends IndexSourceArgs
    case FileIndexSourceArgs(
        url: URL,
        index: String,
        recursive: Boolean = false,
        endpoint: Option[String] = None,
        forceMerge: Option[Int] = None
    ) extends IndexSourceArgs
    case KafkaIndexSourceArgs(
        index: String,
        brokers: List[String],
        topic: String,
        groupId: String,
        offset: Option[SourceOffset],
        options: Option[Map[String, String]] = None
    ) extends IndexSourceArgs
  }

  enum Loglevel {
    case DEBUG extends Loglevel
    case INFO  extends Loglevel
    case WARN  extends Loglevel
    case ERROR extends Loglevel
  }

  object Loglevel {
    given loglevelEncoder: Encoder[Loglevel] = Encoder.encodeString.contramap {
      case Loglevel.DEBUG => "debug"
      case Loglevel.INFO  => "info"
      case Loglevel.WARN  => "warn"
      case Loglevel.ERROR => "error"
    }

    given loglevelDecoder: Decoder[Loglevel] = Decoder.decodeString.map(_.toLowerCase()).emapTry(tryDecode)

    def tryDecode(string: String): Try[Loglevel] = string match {
      case "debug" => Success(Loglevel.DEBUG)
      case "info"  => Success(Loglevel.INFO)
      case "warn"  => Success(Loglevel.WARN)
      case "error" => Success(Loglevel.ERROR)
      case other   => Failure(UserError(s"cannot parse loglevel '$other'"))
    }
  }

  def load(args: List[String]): IO[CliArgs] = for {
    parser <- IO(CliConfig(args))
    _      <- IO(parser.verify())
    opts   <- parser.subcommand match {
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
                source = ApiIndexSourceArgs(host.getOrElse(Hostname("0.0.0.0")), port.getOrElse(Port(8080)))
              )
            }
          case Some(parser.index.file) =>
            for {
              config     <- parse(parser.index.file.config)
              url        <- parse(parser.index.file.url)
              index      <- parse(parser.index.file.index)
              recursive  <- parseOption(parser.index.file.recursive)
              endpoint   <- parseOption(parser.index.file.endpoint)
              loglevel   <- parseOption(parser.index.file.loglevel)
              forceMerge <- parseOption(parser.index.file.forceMerge)
            } yield {
              IndexArgs(
                config = config,
                loglevel = loglevel.getOrElse(INFO),
                source = FileIndexSourceArgs(url, index, recursive.getOrElse(false), endpoint, forceMerge)
              )
            }
          case Some(parser.index.kafka) =>
            for {
              config   <- parse(parser.index.kafka.config)
              index    <- parse(parser.index.kafka.index)
              loglevel <- parseOption(parser.index.kafka.loglevel)
              brokers  <- parse(parser.index.kafka.brokers)
              topic    <- parse(parser.index.kafka.topic)
              groupId  <- parseOption(parser.index.kafka.groupId)
              offset   <- parseOption(parser.index.kafka.offset)
              options  <- parseOption(parser.index.kafka.options)
            } yield {
              IndexArgs(
                config = config,
                loglevel = loglevel.getOrElse(INFO),
                source = IndexSourceArgs.KafkaIndexSourceArgs(
                  index = index,
                  brokers = brokers,
                  topic = topic,
                  groupId = groupId.getOrElse("nixiesearch"),
                  offset = offset,
                  options = options
                )
              )
            }
          case Some(other) =>
            IO.raiseError(new Exception(s"Subcommand $other is not supported. Try indexer api."))
          case None =>
            IO.raiseError(
              new Exception("No command given. If unsure, try 'nixiesearch standalone' or 'nixiesearch --help'.")
            )

        }
      case Some(parser.search) =>
        for {
          config   <- parse(parser.search.config)
          loglevel <- parseOption(parser.search.loglevel)
          api     <- parseOption(parser.search.api)
        } yield {
          SearchArgs(config, loglevel.getOrElse(INFO), api.getOrElse(ApiMode.Http))
        }
      case Some(other) =>
        IO.raiseError(new Exception(s"Subcommand $other is not supported. Try standalone/search/index."))
      case None => IO.raiseError(new Exception("No command given. If unsure, try 'nixiesearch standalone'."))
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
