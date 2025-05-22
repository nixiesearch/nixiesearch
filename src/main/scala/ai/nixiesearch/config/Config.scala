package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema, TextListFieldSchema}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.Loglevel
import ai.nixiesearch.util.{BooleanEnv, EnvVars}
import ai.nixiesearch.util.source.URLReader
import cats.effect.IO
import cats.effect.std.Env
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import cats.syntax.all.*
import io.circe.generic.semiauto.*

import language.experimental.namedTuples
import java.io.File
import io.circe.yaml.parser.*

case class Config(
    inference: InferenceConfig = InferenceConfig(),
    searcher: SearcherConfig = SearcherConfig(),
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
          core = core,
          schema = index.map(i => i.name -> i).toMap
        )
      }
    )
    .ensure(validateModelRefs)

  def validateModelRefs(config: Config): List[String] = {
    val indexRefs = config.schema.values
      .flatMap(mapping =>
        mapping.fields.values.flatMap {
          case field: TextLikeFieldSchema[?] =>
            field.search.semantic.map(p => field.name -> p.model)
          case _ => None
        }
      )
      .toList
    val inferenceRefs = config.inference.embedding.keySet
    indexRefs.filterNot { case (name, ref) => inferenceRefs.contains(ref) }.map { case (name, ref) =>
      s"field $name references a model $ref,  which is not defined in inference config"
    }
  }

  def load(path: URL, env: EnvVars): IO[Config] = for {
    text   <- URLReader.bytes(path).through(fs2.text.utf8.decode).compile.fold("")(_ + _)
    config <- load(text, env)
    _      <- info(s"Loaded config: $path")
  } yield {
    config
  }

  def load(path: File, env: EnvVars): IO[Config] = load(LocalURL(path.toPath), env)

  case class EnvOverride(name: String, lens: (Config, String) => IO[Config]) {
    def patch(config: Config, env: EnvVars): IO[Config] = {
      env.string(name) match {
        case None        => IO.pure(config)
        case Some(value) => lens(config, value).flatTap(_ => info(s"substituted env var $name=$value"))
      }
    }
  }
  val overrides = List(
    EnvOverride(
      "NIXIESEARCH_CORE_PORT",
      (config, port) =>
        IO.fromOption(port.toIntOption)(UserError("port should be a number"))
          .map(port => config.copy(core = config.core.copy(port = Port(port))))
    ),
    EnvOverride(
      "NIXIESEARCH_CORE_HOST",
      (config, host) => IO(config.copy(core = config.core.copy(host = Hostname(host))))
    ),
    EnvOverride(
      "NIXIESEARCH_CORE_LOGLEVEL",
      (config, level) =>
        IO.fromEither(Loglevel.tryDecode(level).toEither)
          .map(level => config.copy(core = config.core.copy(loglevel = level)))
    ),
    EnvOverride(
      "NIXIESEARCH_CORE_TELEMETRY",
      (config, enabled) =>
        BooleanEnv
          .parse(enabled)
          .map(toggle => config.copy(core = config.core.copy(telemetry = config.core.telemetry.copy(usage = toggle))))
    )
  )

  def load(text: String, env: EnvVars): IO[Config] = for {
    yaml        <- IO.fromEither(parse(text))
    decoded     <- IO.fromEither(yaml.as[Config])
    substituted <- overrides.foldLeftM(decoded)((config, envOverride) => envOverride.patch(config, env))
  } yield {
    substituted
  }

}
