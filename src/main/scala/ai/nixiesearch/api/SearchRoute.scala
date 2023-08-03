package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.SearchRoute.{QueryParamDecoder, SearchRequest, SearchResponse, SizeParamDecoder}
import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.store.Store
import ai.nixiesearch.index.store.rw.StoreReader
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.lucene.queryparser.classic.QueryParser

case class SearchRoute(store: Store) extends Route with Logging {
  val routes = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search" =>
      store
        .mapping(indexName)
        .flatMap {
          case Some(mapping) => store.reader(mapping)
          case None          => IO.none
        }
        .flatMap {
          case Some(index) =>
            for {
              query    <- request.as[SearchRequest]
              _        <- info(s"POST /$indexName/_search query=$query")
              start    <- IO(System.currentTimeMillis())
              docs     <- searchDsl(query.query, query.filter, query.fields, index, query.size)
              response <- Ok(SearchResponse.withStartTime(docs, start))
            } yield {
              response
            }
          case None => NotFound(s"index $indexName is missing")
        }

    case GET -> Root / indexName / "_search" :? QueryParamDecoder(query) :? SizeParamDecoder(size) =>
      val b = 1
      store
        .mapping(indexName)
        .flatMap {
          case Some(mapping) => store.reader(mapping)
          case None          => IO.none
        }
        .flatMap {
          case Some(index) =>
            for {
              _     <- info(s"POST /$indexName/_search query=$query size=$size")
              start <- IO(System.currentTimeMillis())
              docs <- query match {
                case Some(q) => searchLucene(q, index, size.getOrElse(10))
                case None    => searchDsl(MatchAllQuery(), Filter(), Nil, index, size.getOrElse(10))
              }

              response <- Ok(SearchResponse.withStartTime(docs, start))
            } yield {
              response
            }
          case None => NotFound(s"index $indexName is missing")
        }
  }

  def searchDsl(query: Query, filter: Filter, fields: List[String], index: StoreReader, n: Int): IO[List[Document]] =
    for {
      storedFields <- fields match {
        case Nil => IO(index.mapping.fields.values.filter(_.store).map(_.name).toList)
        case _   => IO.pure(fields)
      }
      docs <- index.search(query, filters = filter, fields = storedFields, n = n)
    } yield {
      docs
    }

  def searchLucene(query: String, index: StoreReader, n: Int): IO[List[Document]] = for {
    defaultField <- IO.fromOption(index.mapping.fields.values.collectFirst {
      case TextFieldSchema(name, LexicalSearch(_), _, _, _, _)     => name
      case TextListFieldSchema(name, LexicalSearch(_), _, _, _, _) => name
    })(
      new Exception(
        s"no text fields found in schema (existing fields: ${index.mapping.fields.keys.mkString("[", ",", "]")}"
      )
    )
    responseFields <- IO(index.mapping.fields.values.filter(_.store).map(_.name).toList)
    parser         <- IO.pure(new QueryParser(defaultField, index.analyzer))
    query          <- IO(parser.parse(query))
    docs           <- index.search(query, responseFields, n)
  } yield {
    docs
  }

}

object SearchRoute {
  object QueryParamDecoder extends OptionalQueryParamDecoderMatcher[String]("q")
  object SizeParamDecoder  extends OptionalQueryParamDecoderMatcher[Int]("size")

  case class SearchRequest(query: Query, filter: Filter = Filter(), size: Int = 10, fields: List[String] = Nil)
  object SearchRequest {
    implicit val searchRequestEncoder: Encoder[SearchRequest] = deriveEncoder
    implicit val searchRequestDecoder: Decoder[SearchRequest] = Decoder.instance(c =>
      for {
        query  <- c.downField("query").as[Option[Query]].map(_.getOrElse(MatchAllQuery()))
        size   <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
        filter <- c.downField("filter").as[Option[Filter]].map(_.getOrElse(Filter()))
        fields <- c.downField("fields").as[Option[List[String]]].map(_.getOrElse(Nil))
      } yield {
        SearchRequest(query, filter, size, fields)
      }
    )
  }

  case class SearchResponse(took: Long, hits: List[Document]) {}
  object SearchResponse {
    implicit val searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder
    implicit val searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
    def withStartTime(result: List[Document], start: Long) =
      SearchResponse((System.currentTimeMillis() - start).toInt, result)
  }

  implicit val searchRequestDecJson: EntityDecoder[IO, SearchRequest]   = jsonOf
  implicit val searchRequestEncJson: EntityEncoder[IO, SearchRequest]   = jsonEncoderOf
  implicit val searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  implicit val searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

}
