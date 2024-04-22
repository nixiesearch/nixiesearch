package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.{ErrorResponse, SearchRequest}
import ai.nixiesearch.api.query.{MatchAllQuery, MultiMatchQuery, Query}
import ai.nixiesearch.api.ui.WebuiTemplate
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.cluster.Searcher
import ai.nixiesearch.index.NixieIndexSearcher
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{Entity, EntityDecoder, EntityEncoder, Headers, HttpRoutes, MediaType, Request, Response, Status}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

case class WebuiRoute(
    cluster: Searcher,
    searchRoute: SearchRoute,
    tmpl: WebuiTemplate,
    config: Config
) extends Route
    with Logging {

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "_ui" / "assets" / fileName =>
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
    case GET -> Root / "_ui" :? QueryParam(query) :? IndexParam(indexName) =>
      search(Some(indexName), Some(query))
    case GET -> Root / "_ui" =>
      search(None, None)
  }

  def search(indexName: Option[String], queryString: Option[String]) = {
    indexName match {
      case None =>
        for {
          html <- tmpl.empty(indexes = config.search.keys.toList, suggests = Nil)
          _    <- info(s"rendering empty search UI")
        } yield {
          Response[IO](
            headers = Headers(`Content-Type`(MediaType.text.html)),
            entity = Entity.strict(ByteVector(html.getBytes()))
          )
        }
      case Some(indexName) =>
        cluster.indices.get(indexName).flatMap {
          case None => BadRequest(ErrorResponse(s"index $indexName does not exist"))
          case Some(index) =>
            for {

              query    <- makeQuery(index, queryString)
              request  <- makeRequest(index, query)
              response <- index.search(request)
              html <- tmpl.render(
                indexes = config.search.keys.toList,
                index = Some(index.name),
                request,
                response
              )
              _ <- info(s"rendering search UI for index '$indexName' and request $request")
            } yield {
              Response[IO](
                headers = Headers(`Content-Type`(MediaType.text.html)),
                entity = Entity.strict(ByteVector(html.getBytes()))
              )
            }
        }
    }
  }

  def makeRequest(index: NixieIndexSearcher, query: Query): IO[SearchRequest] = for {
    storedFields <- IO(index.index.mapping.fields.toList.collect {
      case (name, schema) if schema.store => name
    })
  } yield {
    SearchRequest(
      query = query,
      fields = storedFields
    )
  }

  def makeQuery(index: NixieIndexSearcher, query: Option[String]): IO[Query] = query match {
    case None => IO(MatchAllQuery())
    case Some(qtext) =>
      for {
        textFields <- IO(index.index.mapping.fields.toList.collect {
          case (name, TextLikeFieldSchema(_, search, _, _, _, _)) if search != NoSearch => name
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
  object IndexParam extends QueryParamDecoderMatcher[String]("index")
}

object WebuiRoute {

  def create(
      cluster: Searcher,
      searchRoute: SearchRoute,
      config: Config
  ): IO[WebuiRoute] =
    WebuiTemplate.create().map(tmpl => WebuiRoute(cluster, searchRoute, tmpl, config))

}
