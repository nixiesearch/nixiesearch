package ai.nixiesearch.config

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestMapping}
import ai.nixiesearch.core.Logging
import cats.effect.IO
import io.circe.{Decoder, Json}
import cats.implicits.*
import org.apache.commons.io.IOUtils

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import io.circe.yaml.parser.*

case class Config(
    api: ApiConfig = ApiConfig(),
    store: StoreConfig = LocalStoreConfig(),
    search: Map[String, IndexMapping] = Map.empty,
    suggest: Map[String, IndexMapping] = Map.empty
)

object Config extends Logging {
  case class ConfigParsingError(msg: String) extends Exception(msg)

  implicit val configDecoder: Decoder[Config] = Decoder.instance(c =>
    for {
      api       <- c.downField("api").as[Option[ApiConfig]].map(_.getOrElse(ApiConfig()))
      store     <- c.downField("store").as[Option[StoreConfig]].map(_.getOrElse(LocalStoreConfig()))
      indexJson <- c.downField("search").as[Option[Map[String, Json]]].map(_.getOrElse(Map.empty))
      index <- indexJson.toList.traverse { case (name, json) =>
        IndexMapping.yaml.indexMappingDecoder(name).decodeJson(json)
      }
      suggestJson <- c.downField("suggest").as[Option[Map[String, Json]]].map(_.getOrElse(Map.empty))
      suggest <- suggestJson.toList.traverse { case (name, json) =>
        SuggestMapping.yaml.suggesterMappingDecoder(name).decodeJson(json)
      }
    } yield {
      Config(api, store, search = index.map(i => i.name -> i).toMap, suggest = suggest.map(s => s.name -> s).toMap)
    }
  )

  def load(pathOption: Option[File]): IO[Config] = pathOption match {
    case Some(file) =>
      for {
        text    <- IO(IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8))
        yaml    <- IO.fromEither(parse(text))
        decoded <- IO.fromEither(yaml.as[Config])
        _       <- info(s"Loaded config: $file")
      } yield {
        decoded
      }
    case None =>
      for {
        _    <- info("No config file given, using defaults")
        dflt <- IO.pure(Config())
        _    <- info(s"Store: ${dflt.store}")
      } yield {
        dflt
      }
  }
}
