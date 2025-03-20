package ai.nixiesearch.api

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, JsonDocumentStream, Logging, PrintProgress}
import ai.nixiesearch.index.Indexer
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import fs2.Stream

case class IndexModifyRoute(indexer: Indexer) extends Route with Logging {
  import IndexModifyRoute.{given, *}

  def nameMatches(name: String): Boolean = indexer.index.mapping.nameMatches(name)

  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case POST -> Root / "v1" / "index" / indexName / "flush" if nameMatches(indexName)  => flush()
    case request @ POST -> Root / "v1" / "index" / name / "merge" if nameMatches(name)  => merge(request)
    case request @ POST -> Root / "v1" / "index" / name if nameMatches(name)            => index(request)
    case request @ POST -> Root / "v1" / "index" / name / "delete" if nameMatches(name) => delete(request)
    case DELETE -> Root / "v1" / "index" / name / "doc" / docid if nameMatches(name)    => delete(docid)
    // legacy
    case POST -> Root / indexName / "_flush" if nameMatches(indexName)            => deprecated() *> flush()
    case request @ POST -> Root / indexName / "_merge" if nameMatches(indexName)  => deprecated() *> merge(request)
    case request @ PUT -> Root / indexName / "_index" if nameMatches(indexName)   => deprecated() *> index(request)
    case request @ POST -> Root / indexName / "_index" if nameMatches(indexName)  => deprecated() *> index(request)
    case request @ POST -> Root / indexName / "_delete" if nameMatches(indexName) => deprecated() *> delete(request)
    case request @ DELETE -> Root / indexName / "_delete" / docid if nameMatches(indexName) =>
      deprecated() *> delete(docid)
  }

  def deprecated(): IO[Unit] = warn("You're using deprecated API endpoint")

  def index(request: Request[IO]): IO[Response[IO]] = for {
    _        <- info(s"PUT /${indexer.index.name.value}/_index")
    ok       <- indexDocStream(request.entity.body.through(JsonDocumentStream.parse(indexer.index.mapping)))
    response <- Ok(ok)
  } yield {
    response
  }

  def delete(docid: String): IO[Response[IO]] = for {
    start    <- IO(System.currentTimeMillis())
    deleted  <- indexer.delete(docid)
    response <- Ok(DeleteResponse("ok", System.currentTimeMillis() - start, deleted))
  } yield {
    response
  }

  def delete(request: Request[IO]): IO[Response[IO]] = for {
    start    <- IO(System.currentTimeMillis())
    delete   <- request.as[DeleteRequest]
    deleted  <- indexer.delete(delete.filters)
    end      <- IO(System.currentTimeMillis())
    response <- Ok(DeleteResponse("ok", end - start, deleted))
  } yield {
    response
  }

  private def indexDocStream(request: Stream[IO, Document]): IO[IndexResponse] = for {
    start <- IO(System.currentTimeMillis())
    docs <- request
      .chunkN(64)
      .through(PrintProgress.tapChunk("indexed docs"))
      .evalTap(chunk => {
        indexer.addDocuments(chunk.toList)
      })
      .map(_.size)
      .compile
      .fold(0)(_ + _)
      .flatTap(d => info(s"completed indexing $d docs, took ${System.currentTimeMillis() - start}ms"))
  } yield {
    IndexResponse.withStartTime("ok", start, docs)
  }

  def flush(): IO[Response[IO]] = for {
    start    <- IO(System.currentTimeMillis())
    _        <- info(s"POST /${indexer.index.name.value}/_flush")
    _        <- indexer.flush()
    _        <- indexer.index.sync()
    response <- Ok(EmptyResponse("ok", System.currentTimeMillis() - start))
  } yield response

  def merge(request: Request[IO]): IO[Response[IO]] = for {
    start <- IO(System.currentTimeMillis())
    req <- request.entity.length match {
      case None | Some(0) => IO(MergeRequest(1))
      case Some(_)        => request.as[MergeRequest]
    }
    _        <- indexer.flush()
    _        <- indexer.merge(req.segments)
    _        <- indexer.index.sync()
    response <- Ok(EmptyResponse("ok", System.currentTimeMillis() - start))
  } yield response

}

object IndexModifyRoute extends Logging {
  case class IndexResponse(status: String, docs: Int, took: Int = 0)
  object IndexResponse {
    def withStartTime(status: String, start: Long, docs: Int) =
      IndexResponse(status, (System.currentTimeMillis() - start).toInt)
  }
  given indexResponseCodec: Codec[IndexResponse] = deriveCodec

  case class DeleteRequest(filters: Option[Filters] = None)

  object DeleteRequest {
    given deleteRequestEncoder: Encoder[DeleteRequest]               = deriveEncoder
    given deleteRequestDecoder: Decoder[DeleteRequest]               = deriveDecoder
    given deleteRequestEncoderJson: EntityDecoder[IO, DeleteRequest] = jsonOf
    given deleteRequestDecoderJson: EntityEncoder[IO, DeleteRequest] = jsonEncoderOf
  }

  case class DeleteResponse(status: String, took: Long, deleted: Int)

  object DeleteResponse {
    given deleteResponseCodec: Codec[DeleteResponse]                   = deriveCodec
    given deleteResponseEncoderJson: EntityEncoder[IO, DeleteResponse] = jsonEncoderOf
    given deleteResponseDecoderJson: EntityDecoder[IO, DeleteResponse] = jsonOf
  }

  import ai.nixiesearch.config.mapping.IndexMapping.json.given

  given schemaEncoderJson: EntityEncoder[IO, IndexMapping]         = jsonEncoderOf
  given schemaDecoderJson: EntityDecoder[IO, IndexMapping]         = jsonOf
  given indexResponseEncoderJson: EntityEncoder[IO, IndexResponse] = jsonEncoderOf
  given indexResponseDecoderJson: EntityDecoder[IO, IndexResponse] = jsonOf

  case class MergeRequest(segments: Int)
  given mergeRequestCodec: Codec[MergeRequest]            = deriveCodec
  given mergeRequestJson: EntityDecoder[IO, MergeRequest] = jsonOf

  case class EmptyResponse(status: String, took: Long)
  given okResponseCodec: Codec[EmptyResponse]               = deriveCodec
  given okResponseJsonEnc: EntityEncoder[IO, EmptyResponse] = jsonEncoderOf
  given okResponseJson: EntityDecoder[IO, EmptyResponse]    = jsonOf

}
