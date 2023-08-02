package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.MappingConfig
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class IndexConfig(mapping: MappingConfig = MappingConfig())

object IndexConfig {
  case class MappingConfig(dynamic: Boolean = false)

  implicit val mappingConfigEncoder: Encoder[MappingConfig] = deriveEncoder
  implicit val mappingConfigDecoder: Decoder[MappingConfig] = Decoder.instance(c =>
    for {
      dynamic <- c.downField("dynamic").as[Option[Boolean]].map(_.getOrElse(false))
    } yield {
      MappingConfig(dynamic)
    }
  )

  implicit val indexConfigDecoder: Decoder[IndexConfig] = Decoder.instance(c =>
    for {
      mapping <- c.downField("mapping").as[Option[MappingConfig]].map(_.getOrElse(MappingConfig()))
    } yield {
      IndexConfig(mapping)
    }
  )

  implicit val indexConfigEncoder: Encoder[IndexConfig] = deriveEncoder
}
