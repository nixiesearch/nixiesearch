package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}

import scala.util.{Failure, Success}
import io.circe.{Decoder, Encoder}
import ai.nixiesearch.config.Config.ConfigParsingError

case class ApiConfig(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080))

object ApiConfig {
  case class Hostname(value: String)

  object Hostname {
    given hostnameDecoder: Decoder[Hostname] = Decoder.decodeString.emapTry {
      case ""    => Failure(ConfigParsingError("hostname cannot be empty"))
      case other => Success(Hostname(other))
    }
  }

  case class Port(value: Int)

  object Port {
    given portDecoder: Decoder[Port] = Decoder.decodeInt.emapTry {
      case port if port > 0 && port < 65536 => Success(Port(port))
      case other                            => Failure(ConfigParsingError(s"port $other should be in 0..65536 range"))
    }
  }

  given apiConfigDecoder: Decoder[ApiConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Option[Hostname]].map(_.getOrElse(ApiConfig().host))
      port <- c.downField("port").as[Option[Port]].map(_.getOrElse(ApiConfig().port))
    } yield {
      ApiConfig(host, port)
    }
  )
}
