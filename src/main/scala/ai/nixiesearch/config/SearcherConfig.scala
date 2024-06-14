package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*

case class SearcherConfig(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080))

object SearcherConfig {
  given searcherConfigEncoder: Encoder[SearcherConfig] = deriveEncoder
  given searcherConfigDecoder: Decoder[SearcherConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Option[Hostname]]
      port <- c.downField("port").as[Option[Port]]
    } yield {
      SearcherConfig(host.getOrElse(Hostname("0.0.0.0")), port.getOrElse(Port(8080)))
    }
  )
}
