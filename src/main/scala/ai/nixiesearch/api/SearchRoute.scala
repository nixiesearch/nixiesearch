package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.SearchRoute.{QueryParamDecoder, SearchRequest, SearchResponse, SizeParamDecoder}
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.aggregator.AggregationResult
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.{IndexReader, IndexRegistry}
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.lucene.queryparser.classic.QueryParser

case class SearchRoute(indices: IndexRegistry) extends Route with Logging {
  val routes = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search" =>
      indices.reader(indexName).flatMap {
        case Some(index) =>
          for {
            query    <- request.as[SearchRequest]
            _        <- info(s"POST /$indexName/_search query=$query")
            docs     <- searchDsl(query.query, query.filter, query.fields, index, query.size)
            response <- Ok(docs)
          } yield {
            response
          }
        case None => NotFound(s"index $indexName is missing")
      }

    case GET -> Root / indexName / "_search" :? QueryParamDecoder(query) :? SizeParamDecoder(size) =>
      indices.reader(indexName).flatMap {
        case Some(index) =>
          for {
            _     <- info(s"POST /$indexName/_search query=$query size=$size")
            start <- IO(System.currentTimeMillis())
            docs <- query match {
              case Some(q) => searchLucene(q, index, size.getOrElse(10))
              case None    => searchDsl(MatchAllQuery(), Filter(), Nil, index, size.getOrElse(10))
            }

            response <- Ok(docs)
          } yield {
            response
          }
        case None => NotFound(s"index $indexName is missing")
      }
  }

  def searchDsl(query: Query, filter: Filter, fields: List[String], index: IndexReader, n: Int): IO[SearchResponse] =
    for {
      mapping <- index.mapping()
      storedFields <- fields match {
        case Nil => IO(mapping.fields.values.filter(_.store).map(_.name).toList)
        case _   => IO.pure(fields)
      }
      response <- index.search(query, filters = filter, fields = storedFields, n = n)
    } yield {
      response
    }

  def searchLucene(query: String, index: IndexReader, n: Int): IO[SearchResponse] = for {
    mapping <- index.mapping()
    defaultField <- IO.fromOption(mapping.fields.values.collectFirst {
      case TextFieldSchema(name, LexicalSearch(_), _, _, _, _)     => name
      case TextListFieldSchema(name, LexicalSearch(_), _, _, _, _) => name
    })(
      new Exception(
        s"no text fields found in schema (existing fields: ${mapping.fields.keys.mkString("[", ",", "]")}"
      )
    )
    responseFields <- IO(mapping.fields.values.filter(_.store).map(_.name).toList)
    parser         <- IO.pure(new QueryParser(defaultField, index.analyzer))
    query          <- IO(parser.parse(query))
    response       <- index.search(query, responseFields, n, Aggs())
  } yield {
    response
  }

}

object SearchRoute {
  object QueryParamDecoder extends OptionalQueryParamDecoderMatcher[String]("q")
  object SizeParamDecoder  extends OptionalQueryParamDecoderMatcher[Int]("size")

  case class SearchRequest(
      query: Query,
      filter: Filter = Filter(),
      size: Int = 10,
      fields: List[String] = Nil,
      aggs: Aggs = Aggs()
  )
  object SearchRequest {
    implicit val searchRequestEncoder: Encoder[SearchRequest] = deriveEncoder
    implicit val searchRequestDecoder: Decoder[SearchRequest] = Decoder.instance(c =>
      for {
        query  <- c.downField("query").as[Option[Query]].map(_.getOrElse(MatchAllQuery()))
        size   <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
        filter <- c.downField("filter").as[Option[Filter]].map(_.getOrElse(Filter()))
        fields <- c.downField("fields").as[Option[List[String]]].map(_.getOrElse(Nil))
        aggs   <- c.downField("aggs").as[Option[Aggs]].map(_.getOrElse(Aggs()))
      } yield {
        SearchRequest(query, filter, size, fields, aggs)
      }
    )
  }

  case class SearchResponse(took: Long, hits: List[Document], aggs: Map[String, AggregationResult]) {}
  object SearchResponse {
    given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder
    given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
  }

  given searchRequestDecJson: EntityDecoder[IO, SearchRequest]   = jsonOf
  given searchRequestEncJson: EntityEncoder[IO, SearchRequest]   = jsonEncoderOf
  given searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  given searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

}
