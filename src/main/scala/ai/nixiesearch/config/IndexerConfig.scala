package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.IndexerConfig.IndexerSourceConfig
import ai.nixiesearch.config.IndexerConfig.IndexerSourceConfig.{ApiSourceConfig, FileSourceConfig}
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.*

case class IndexerConfig(source: IndexerSourceConfig = ApiSourceConfig())

object IndexerConfig {
  enum IndexerSourceConfig {
    case ApiSourceConfig(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080)) extends IndexerSourceConfig
    case FileSourceConfig(path: URL, recursive: Boolean = false)                        extends IndexerSourceConfig
  }

  given apiSourceDecoder: Decoder[ApiSourceConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Option[Hostname]]
      port <- c.downField("port").as[Option[Port]]
    } yield {
      ApiSourceConfig(host = host.getOrElse(Hostname("0.0.0.0")), port = port.getOrElse(Port(8080)))
    }
  )

  given fileSourceDecoder: Decoder[FileSourceConfig] = Decoder.instance(c =>
    for {
      path      <- c.downField("path").as[URL]
      recursive <- c.downField("recursive").as[Option[Boolean]]
    } yield {
      FileSourceConfig(path, recursive.getOrElse(false))
    }
  )

  given indexerConfigDecoder: Decoder[IndexerConfig] = Decoder.instance(c => {
    c.downField("api").focus match {
      case Some(json) => apiSourceDecoder.decodeJson(json).map(source => IndexerConfig(source))
      case None =>
        c.downField("file").focus match {
          case Some(json) => fileSourceDecoder.decodeJson(json).map(source => IndexerConfig(source))
          case None       => Left(DecodingFailure(s"cannot decode indexer source config", c.history))
        }
    }
  })
}
