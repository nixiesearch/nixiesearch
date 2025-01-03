package ai.nixiesearch.config

import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.config.mapping.SearchType.SemanticSearchLikeType
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import cats.implicits.*
import io.circe.generic.semiauto.*
import language.experimental.namedTuples
import java.io.File
import io.circe.yaml.parser.*

case class Config(
    inference: InferenceConfig = InferenceConfig(),
    searcher: SearcherConfig = SearcherConfig(),
    indexer: IndexerConfig = IndexerConfig(),
    core: CoreConfig = CoreConfig(),
    schema: Map[IndexName, IndexMapping] = Map.empty
)

object Config extends Logging {
  import IndexMapping.json.given
  case class ConfigParsingError(msg: String) extends Exception(msg)
  given configEncoder: Encoder[Config] = deriveEncoder
  given configDecoder: Decoder[Config] = Decoder
    .instance(c =>
      for {
        inference <- c.downField("inference").as[Option[InferenceConfig]].map(_.getOrElse(InferenceConfig()))
        searcher  <- c.downField("searcher").as[Option[SearcherConfig]].map(_.getOrElse(SearcherConfig()))
        indexer   <- c.downField("indexer").as[Option[IndexerConfig]].map(_.getOrElse(IndexerConfig()))
        core      <- c.downField("core").as[Option[CoreConfig]].map(_.getOrElse(CoreConfig()))
        indexJson <- c.downField("schema").as[Option[Map[IndexName, Json]]].flatMap {
          case Some(map) => Right(map)
          case _ =>
            logger.info("No index schemas found in the config file.")
            logger.info(
              "It's OK if you use Nixiesearch as an inference endpoint, but not OK if you plan to index documents"
            )
            logger.info("You have to first define an index schema before indexing documents.")
            Right(Map.empty)
        }
        index <- indexJson.toList.traverse { case (name, json) =>
          IndexMapping.yaml.indexMappingDecoder(name).decodeJson(json)
        }
      } yield {
        Config(
          inference = inference,
          searcher = searcher,
          indexer = indexer,
          core = core,
          schema = index.map(i => i.name -> i).toMap
        )
      }
    )
    .ensure(validateModelRefs)

  def validateModelRefs(config: Config): List[String] = {
    val indexRefs = config.schema.values
      .flatMap(mapping =>
        mapping.fields.values.collect {
          case field @ TextLikeFieldSchema(search=SemanticSearchLikeType(ref)) =>
            field.name -> ref
        }
      )
      .toList
    val inferenceRefs = config.inference.embedding.keySet
    indexRefs.filterNot { case (name, ref) => inferenceRefs.contains(ref) }.map { case (name, ref) =>
      s"field $name references a model $ref,  which is not defined in inference config"
    }
  }

  def load(path: URL): IO[Config] = for {
    text   <- URLReader.bytes(path).through(fs2.text.utf8.decode).compile.fold("")(_ + _)
    config <- load(text)
    _      <- info(s"Loaded config: $path")
  } yield {
    config
  }

  def load(path: File): IO[Config] = load(LocalURL(path.toPath))

  def load(text: String): IO[Config] = for {
    yaml    <- IO.fromEither(parse(text))
    decoded <- IO.fromEither(yaml.as[Config])
  } yield {
    decoded
  }
}
