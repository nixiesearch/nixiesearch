package ai.nixiesearch.config

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import cats.effect.IO
import io.circe.{Decoder, Json}
import cats.implicits.*
import org.apache.commons.io.IOUtils

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import io.circe.yaml.parser.*

case class Config(
    searcher: SearcherConfig = SearcherConfig(),
    indexer: IndexerConfig = IndexerConfig(),
    core: CoreConfig = CoreConfig(),
    schema: Map[String, IndexMapping] = Map.empty
)

object Config extends Logging {
  case class ConfigParsingError(msg: String) extends Exception(msg)

  implicit val configDecoder: Decoder[Config] = Decoder.instance(c =>
    for {
      searcher  <- c.downField("searcher").as[Option[SearcherConfig]].map(_.getOrElse(SearcherConfig()))
      indexer   <- c.downField("indexer").as[Option[IndexerConfig]].map(_.getOrElse(IndexerConfig()))
      core      <- c.downField("core").as[Option[CoreConfig]].map(_.getOrElse(CoreConfig()))
      indexJson <- c.downField("schema").as[Option[Map[String, Json]]].map(_.getOrElse(Map.empty))
      index <- indexJson.toList.traverse { case (name, json) =>
        IndexMapping.yaml.indexMappingDecoder(name).decodeJson(json)
      }
    } yield {
      Config(
        searcher = searcher,
        indexer = indexer,
        core = core,
        schema = index.map(i => i.name -> i).toMap
      )
    }
  )

  def load(path: File): IO[Config] = {
    for {
      text    <- IO(IOUtils.toString(new FileInputStream(path), StandardCharsets.UTF_8))
      yaml    <- IO.fromEither(parse(text))
      decoded <- IO.fromEither(yaml.as[Config])
      _       <- info(s"Loaded config: $path")
    } yield {
      decoded
    }
  }
}
