package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.QueryParamDecoder
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexBuilder
import cats.effect.IO
import io.circe.{Codec, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*

case class SearchRoute(indices: Map[String, IndexBuilder]) extends Route with Logging {
  val routes = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search"                   => searchDsl(request, indexName)
    case POST -> Root / indexName / "_search" :? QueryParamDecoder(query) => searchLucene(query, indexName)
  }

  def searchDsl(request: Request[IO], indexName: String): IO[Response[IO]]     = ???
  def searchLucene(query: Option[String], indexName: String): IO[Response[IO]] = ???

}

object SearchRoute {
  object QueryParamDecoder extends OptionalQueryParamDecoderMatcher[String]("q")
  case class SearchRequest()
}
