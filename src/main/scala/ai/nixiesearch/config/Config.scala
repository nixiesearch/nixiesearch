package ai.nixiesearch.config

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import io.circe.{Decoder, Json}
import cats.implicits.*

case class Config(
    api: ApiConfig = ApiConfig(),
    store: StoreConfig = LocalStoreConfig(),
    search: Map[String, IndexMapping] = Map.empty
)

object Config {
  case class ConfigParsingError(msg: String) extends Exception(msg)

  implicit val configDecoder: Decoder[Config] = Decoder.instance(c =>
    for {
      api       <- c.downField("api").as[Option[ApiConfig]].map(_.getOrElse(ApiConfig()))
      store     <- c.downField("store").as[Option[StoreConfig]].map(_.getOrElse(LocalStoreConfig()))
      indexJson <- c.downField("search").as[Option[Map[String, Json]]].map(_.getOrElse(Map.empty))
      index <- indexJson.toList.traverse { case (name, json) =>
        IndexMapping.yaml.indexMappingDecoder(name).decodeJson(json)
      }
    } yield {
      Config(api, store, index.map(i => i.name -> i).toMap)
    }
  )
}
