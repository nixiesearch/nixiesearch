package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.{IndexBuilder, IndexBuilderRegistry}
import cats.effect.IO
import io.circe.{Codec, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*

case class IndexRoute(indices: IndexBuilderRegistry) extends Route with Logging {
  import IndexRoute._

  val routes = HttpRoutes.of[IO] {
    case POST -> Root / indexName / "_flush"          => flush(indexName)
    case request @ PUT -> Root / indexName / "_index" => index(request, indexName)
    case GET -> Root / indexName / "_mapping"         => mapping(indexName)
  }

  def index(request: Request[IO], indexName: String): IO[Response[IO]] = for {
    start <- IO(System.currentTimeMillis())
    docs  <- request.as[List[Document]].handleErrorWith(_ => request.as[Document].flatMap(doc => IO.pure(List(doc))))
    _     <- info(s"PUT /$indexName/_index, payload: ${docs.size} docs")
    writer <- indices.get(indexName).flatMap {
      case Some(value) => IO.pure(value)
      case None        => IO.raiseError(new Exception(s"index $indexName is missing"))
    }
    _        <- IO(writer.addDocuments(docs))
    response <- Ok(IndexResponse.withStartTime("created", start))
  } yield {
    response
  }

  def mapping(indexName: String): IO[Response[IO]] =
    indices.get(indexName).flatMap {
      case Some(index) => info(s"GET /$indexName/_mapping") *> Ok(index.schema)
      case None        => NotFound(s"index $indexName is missing in config file")
    }

  def flush(indexName: String): IO[Response[IO]] =
    indices.get(indexName).flatMap {
      case Some(index) =>
        for {
          start    <- IO(System.currentTimeMillis())
          _        <- info(s"POST /$indexName/_flush")
          _        <- IO(index.writer.flush())
          response <- Ok(IndexResponse.withStartTime("flushed", start))
        } yield {
          response
        }
      case None => NotFound(s"index $indexName is missing in config file")
    }
}

object IndexRoute {
  case class IndexResponse(result: String, took: Int = 0)
  object IndexResponse {
    def withStartTime(result: String, start: Long) = IndexResponse(result, (System.currentTimeMillis() - start).toInt)
  }
  implicit val indexResponseCodec: Codec[IndexResponse] = deriveCodec

  import ai.nixiesearch.config.mapping.IndexMapping.json._

  implicit val schemaJson: EntityEncoder[IO, IndexMapping]                = jsonEncoderOf
  implicit val singleDocJson: EntityDecoder[IO, Document]                 = jsonOf
  implicit val docListJson: EntityDecoder[IO, List[Document]]             = jsonOf
  implicit val indexResponseEncoderJson: EntityEncoder[IO, IndexResponse] = jsonEncoderOf
  implicit val indexResponseDecoderJson: EntityDecoder[IO, IndexResponse] = jsonOf
}
