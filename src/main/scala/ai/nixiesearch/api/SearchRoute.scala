package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestProcess
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.api.SearchRoute.{ErrorResponse, SearchRequest, SearchResponse}
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.aggregate.AggregationResult
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.Searcher
import ai.nixiesearch.index.sync.Index
import cats.effect.{IO, Ref}
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{Entity, EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.lucene.queryparser.classic.QueryParser

case class SearchRoute(searcher: Searcher) extends Route with Logging {
  val emptyRequest = SearchRequest(query = MatchAllQuery())
  val routes = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search" if indexName == searcher.index.name =>
      search(request)
  }

  def search(request: Request[IO]): IO[Response[IO]] = for {
    query <- IO(request.entity.length).flatMap {
      case None    => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(0) => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(_) => request.as[SearchRequest]
    }
    _        <- info(s"search index='${searcher.index.name}' query=$query")
    response <- searcher.search(query).flatMap(docs => Ok(docs))
  } yield {
    response
  }

}

object SearchRoute {

  case class SearchRequest(
      query: Query,
      filters: Filters = Filters(),
      size: Int = 10,
      fields: List[String] = Nil,
      aggs: Aggs = Aggs()
  )
  object SearchRequest {
    given searchRequestEncoder: Encoder[SearchRequest] = deriveEncoder
    given searchRequestDecoder: Decoder[SearchRequest] = Decoder.instance(c =>
      for {
        query   <- c.downField("query").as[Option[Query]].map(_.getOrElse(MatchAllQuery()))
        size    <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
        filters <- c.downField("filters").as[Option[Filters]].map(_.getOrElse(Filters()))
        fields <- c.downField("fields").as[Option[List[String]]].map {
          case Some(Nil)  => Nil
          case Some(list) => list
          case None       => Nil
        }
        aggs <- c.downField("aggs").as[Option[Aggs]].map(_.getOrElse(Aggs()))
      } yield {
        SearchRequest(query, filters, size, fields, aggs)
      }
    )
  }

  case class SearchResponse(took: Long, hits: List[Document], aggs: Map[String, AggregationResult]) {}
  object SearchResponse {
    given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder
    given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
  }
  import SearchRequest.given

  given searchRequestDecJson: EntityDecoder[IO, SearchRequest]   = jsonOf
  given searchRequestEncJson: EntityEncoder[IO, SearchRequest]   = jsonEncoderOf
  given searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  given searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

  case class ErrorResponse(error: String)
  object ErrorResponse {
    given errorResponseCodec: Codec[ErrorResponse]            = deriveCodec
    given errorResponseJson: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf
  }

  case class SuggestRequest(
      query: String,
      fields: List[String] = Nil,
      count: Int = 10,
      process: Option[SuggestProcess] = None
  )
  object SuggestRequest {
    case class SuggestProcess(deduplicate: Option[Deduplicate] = None, rerank: Option[Rerank] = None)
    case class Deduplicate(caseSensitive: Boolean = false)
    case class Rerank(depth: Int = 100)

    given rerankEncoder: Encoder[Rerank] = deriveEncoder
    given rerankDecoder: Decoder[Rerank] = Decoder.instance(c =>
      for {
        depth <- c.downField("depth").as[Option[Int]]
      } yield {
        Rerank(depth.getOrElse(100))
      }
    )
    given deduplicateEncoder: Encoder[Deduplicate] = deriveEncoder
    given deduplicateDecoder: Decoder[Deduplicate] = Decoder.instance(c =>
      for {
        caseSensitive <- c.downField("caseSensitive").as[Option[Boolean]]
      } yield {
        Deduplicate(caseSensitive.getOrElse(false))
      }
    )
    given processEncoder: Encoder[SuggestProcess] = deriveEncoder
    given processDecoder: Decoder[SuggestProcess] = Decoder.instance(c =>
      for {
        dedup  <- c.downField("deduplicate").as[Option[Deduplicate]]
        rerank <- c.downField("rerank").as[Option[Rerank]]
      } yield {
        SuggestProcess(dedup, rerank)
      }
    )
    given suggestRequestEncoder: Encoder[SuggestRequest] = deriveEncoder
    given suggestRequestDecoder: Decoder[SuggestRequest] = Decoder.instance(c =>
      for {
        query   <- c.downField("query").as[String]
        fields  <- c.downField("fields").as[Option[List[String]]]
        count   <- c.downField("count").as[Option[Int]]
        process <- c.downField("process").as[Option[SuggestProcess]]
      } yield {
        SuggestRequest(query, fields.getOrElse(Nil), count.getOrElse(10), process)
      }
    )
  }

  case class SuggestResponse(suggestions: List[Suggestion], took: Long)
  object SuggestResponse {
    case class Suggestion(text: String, score: Float)

    given suggestionCodec: Codec[Suggestion]              = deriveCodec
    given suggestionResponseCodec: Codec[SuggestResponse] = deriveCodec
  }

  given suggestRequestDecJson: EntityDecoder[IO, SuggestRequest] = jsonOf
  given suggestRequestEncJson: EntityEncoder[IO, SuggestRequest] = jsonEncoderOf

  given suggestResponseEncJson: EntityEncoder[IO, SuggestResponse] = jsonEncoderOf
  given suggestResponseDecJson: EntityDecoder[IO, SuggestResponse] = jsonOf

}
