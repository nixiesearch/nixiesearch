package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, JsonDocumentStream, Logging, PrintProgress}
import ai.nixiesearch.index.IndexRegistry
import cats.effect.IO
import io.circe.{Codec, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import fs2.Stream

case class IndexRoute(registry: IndexRegistry) extends Route with Logging {
  import IndexRoute.{given, *}

  val routes = HttpRoutes.of[IO] {
    case POST -> Root / indexName / "_flush"          => flush(indexName)
    case request @ PUT -> Root / indexName / "_index" => handleIndex(request, indexName)
    case GET -> Root / indexName / "_mapping"         => handleMapping(indexName)
  }

  def handleIndex(request: Request[IO], indexName: String): IO[Response[IO]] = for {
    _        <- info(s"PUT /$indexName/_index")
    ok       <- index(request.entity.body.through(JsonDocumentStream.parse), indexName)
    response <- Ok(ok)
  } yield {
    response
  }

  def index(request: Stream[IO, Document], indexName: String): IO[IndexResponse] = for {
    start   <- IO(System.currentTimeMillis())
    mapping <- registry.mapping(indexName)
    _ <- request
      .chunkN(64)
      .unchunks
      .through(PrintProgress.tap("indexed docs"))
      .chunkN(64)
      .evalScan(mapping)((mappingOption, chunk) =>
        for {
          mapping <- getMappingOrCreate(mappingOption, chunk.toList, indexName)
          writer  <- registry.writer(mapping)
          _       <- writer.addDocuments(chunk.toList)
        } yield {
          Some(mapping)
        }
      )
      .compile
      .drain
  } yield {
    IndexResponse.withStartTime("created", start)
  }

  def handleMapping(indexName: String): IO[Response[IO]] =
    mapping(indexName).flatMap {
      case Some(index) => info(s"GET /$indexName/_mapping") *> Ok(index)
      case None        => NotFound(s"index $indexName is missing in config file")
    }

  def mapping(indexName: String): IO[Option[IndexMapping]] = registry.mapping(indexName)

  def flush(indexName: String): IO[Response[IO]] = {
    registry.mapping(indexName).flatMap {
      case None => NotFound(s"index $indexName is missing in config file")
      case Some(mapping) =>
        registry.writer(mapping).flatMap { writer =>
          for {
            start    <- IO(System.currentTimeMillis())
            _        <- info(s"POST /$indexName/_flush")
            _        <- writer.flush()
            response <- Ok(IndexResponse.withStartTime("flushed", start))
          } yield {
            response
          }

        }

    }
  }

  private def getMappingOrCreate(
      mappingOption: Option[IndexMapping],
      first: List[Document],
      indexName: String
  ): IO[IndexMapping] =
    mappingOption match {
      case Some(existing) =>
        existing.config.mapping.dynamic match {
          case false => IO.pure(existing)
          case true =>
            for {
              updated <- IndexMapping.fromDocument(first, indexName)
              merged  <- existing.dynamic(updated)
              writer  <- registry.writer(merged)
              _       <- IO.whenA(merged != existing)(writer.refreshMapping(merged))
            } yield {
              merged
            }
        }
      case None =>
        for {
          _ <- warn(s"Index '$indexName' mapping not found, using dynamic mapping")
          _ <- warn("Dynamic mapping is only recommended for testing. Prefer explicit mapping definition in config.")
          generated <- IndexMapping.fromDocument(first, indexName).map(_.withDynamicMapping(true))
          _         <- info(s"Generated mapping $generated")
          writer    <- registry.writer(generated)
          _         <- writer.refreshMapping(generated)
        } yield {
          generated
        }
    }
}

object IndexRoute {
  case class IndexResponse(result: String, took: Int = 0)
  object IndexResponse {
    def withStartTime(result: String, start: Long) = IndexResponse(result, (System.currentTimeMillis() - start).toInt)
  }
  given indexResponseCodec: Codec[IndexResponse] = deriveCodec

  import ai.nixiesearch.config.mapping.IndexMapping.json.given

  given schemaEncoderJson: EntityEncoder[IO, IndexMapping]         = jsonEncoderOf
  given schemaDecoderJson: EntityDecoder[IO, IndexMapping]         = jsonOf
  given singleDocJson: EntityDecoder[IO, Document]                 = jsonOf
  given docListJson: EntityDecoder[IO, List[Document]]             = jsonOf
  given indexResponseEncoderJson: EntityEncoder[IO, IndexResponse] = jsonEncoderOf
  given indexResponseDecoderJson: EntityDecoder[IO, IndexResponse] = jsonOf
}
