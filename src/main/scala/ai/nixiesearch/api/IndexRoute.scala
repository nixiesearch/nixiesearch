package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, JsonDocumentStream, Logging, PrintProgress}
import ai.nixiesearch.index.Indexer
import cats.effect.IO
import io.circe.{Codec, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import fs2.Stream

case class IndexRoute(indexer: Indexer) extends Route with Logging {
  import IndexRoute.{given, *}

  val routes = HttpRoutes.of[IO] {
    case POST -> Root / indexName / "_flush" if indexName == indexer.index.name           => flush(indexName)
    case request @ PUT -> Root / indexName / "_index" if indexName == indexer.index.name  => index(request, indexName)
    case request @ POST -> Root / indexName / "_index" if indexName == indexer.index.name => index(request, indexName)
    case GET -> Root / indexName / "_mapping" if indexName == indexer.index.name          => mapping(indexName)
  }

  def index(request: Request[IO], indexName: String): IO[Response[IO]] = for {
    _        <- info(s"PUT /$indexName/_index")
    ok       <- indexDocStream(request.entity.body.through(JsonDocumentStream.parse), indexName)
    response <- Ok(ok)
  } yield {
    response
  }

  private def indexDocStream(request: Stream[IO, Document], indexName: String): IO[IndexResponse] = for {
    start <- IO(System.currentTimeMillis())
    _ <- request
      .through(PrintProgress.tap("indexed docs"))
      .chunkN(64)
      .evalMap(chunk => {
        val b = 1
        indexer.addDocuments(chunk.toList)
      })
      .compile
      .drain
      .flatTap(_ => info(s"completed indexing, took ${System.currentTimeMillis() - start}ms"))
  } yield {
    IndexResponse.withStartTime("created", start)
  }

  def mapping(indexName: String): IO[Response[IO]] = {
    Ok(indexer.index.mapping)
  }

  def flush(indexName: String): IO[Response[IO]] = for {
    _        <- indexer.flush()
    _        <- indexer.index.sync()
    response <- Ok()
  } yield response

}

object IndexRoute extends Logging {
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
