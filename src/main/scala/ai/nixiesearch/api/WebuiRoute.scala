package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.{ErrorResponse, SearchRequest}
import ai.nixiesearch.api.query.{MatchAllQuery, MultiMatchQuery, Query}
import ai.nixiesearch.api.ui.WebuiTemplate
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.Searcher
import cats.effect.{IO, Ref}
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{Entity, EntityDecoder, EntityEncoder, Headers, HttpRoutes, MediaType, Request, Response, Status}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.http4s.headers.`Content-Type`
import org.http4s.server.websocket.WebSocketBuilder
import scodec.bits.ByteVector

case class WebuiRoute(
    searcher: Searcher,
    tmpl: WebuiTemplate
) extends Route
    with Logging {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / indexName / "_ui" / "assets" / fileName if indexName == searcher.index.name.value =>
      for {
        bytes <- IO(IOUtils.resourceToByteArray(s"/ui/assets/$fileName"))
        _     <- info(s"GET assets/$fileName")
      } yield {
        val mediaType =
          MediaType.forExtension(FilenameUtils.getExtension(fileName)).getOrElse(MediaType.application.`octet-stream`)
        Response[IO](
          headers = Headers(`Content-Type`(mediaType)),
          entity = Entity.strict(ByteVector(bytes))
        )
      }
    case GET -> Root / indexName / "_ui" :? QueryParam(query) if indexName == searcher.index.name.value =>
      search(Some(query))
    case GET -> Root / indexName / "_ui" if indexName == searcher.index.name.value =>
      search(None)
  }

  def search(queryString: Option[String]) = {
    for {
      query    <- makeQuery(searcher, queryString)
      request  <- makeRequest(searcher, query)
      response <- searcher.search(request)
      html <- tmpl.render(
        index = searcher.index.name.value,
        request,
        response
      )
      _ <- info(s"rendering search UI for index '${searcher.index.name}' and request $request")
    } yield {
      Response[IO](
        headers = Headers(`Content-Type`(MediaType.text.html)),
        entity = Entity.strict(ByteVector(html.getBytes()))
      )
    }
  }

  def makeRequest(index: Searcher, query: Query): IO[SearchRequest] = for {
    storedFields <- IO(index.index.mapping.fields.toList.collect {
      case (name, schema) if schema.store => name
    })
  } yield {
    SearchRequest(
      query = query,
      fields = storedFields
    )
  }

  def makeQuery(index: Searcher, query: Option[String]): IO[Query] = query match {
    case None => IO(MatchAllQuery())
    case Some(qtext) =>
      for {
        textFields <- IO(index.index.mapping.fields.toList.collect {
          case (name, TextLikeFieldSchema(_, search, _, _, _, _, _, _)) if search != NoSearch => name
        })
        query <- textFields match {
          case Nil =>
            warn(s"index mapping for index=${index.name} has no searchable text fields") *> IO(MatchAllQuery())
          case _ => IO(MultiMatchQuery(query = qtext, fields = textFields))
        }
      } yield {
        query
      }
  }

  object QueryParam extends QueryParamDecoderMatcher[String]("query")
}

object WebuiRoute {
  lazy val template                         = WebuiTemplate()
  def apply(searcher: Searcher): WebuiRoute = WebuiRoute(searcher, template)

}
