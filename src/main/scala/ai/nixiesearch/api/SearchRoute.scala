package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.RRFOptions
import ai.nixiesearch.api.SearchRoute.{ErrorResponse, SearchRequest, SearchResponse, SuggestRequest}
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.core.aggregate.AggregationResult
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.Searcher
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import ai.nixiesearch.index.sync.Index
import cats.effect.{IO, Ref}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.http4s.{Entity, EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*

case class SearchRoute(searcher: Searcher) extends Route with Logging {
  val emptyRequest = SearchRequest(query = MatchAllQuery())
  val routes = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search" if indexName == searcher.index.name =>
      search(request)
    case request @ POST -> Root / indexName / "_suggest" if indexName == searcher.index.name =>
      suggest(request)
  }

  def suggest(request: Request[IO]): IO[Response[IO]] = for {
    query    <- request.as[SuggestRequest]
    _        <- info(s"suggest index='${searcher.index.name}' query=$query")
    response <- searcher.suggest(query).flatMap(docs => Ok(docs))
  } yield {
    response
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
      rerank: SuggestRerankOptions = RRFOptions()
  )
  object SuggestRequest {
    enum SuggestRerankOptions {
      case RRFOptions(depth: Int = 50, scale: Float = 60.0)     extends SuggestRerankOptions
      case LTROptions(depth: Int = 50, model: String = "small") extends SuggestRerankOptions
    }

    object SuggestRerankOptions {
      given rrfProcessEncoder: Encoder[RRFOptions] = deriveEncoder
      given ltrProcessEncoder: Encoder[LTROptions] = deriveEncoder
      given rrfProcessDecoder: Decoder[RRFOptions] = Decoder.instance(c =>
        for {
          depth         <- c.downField("depth").as[Option[Int]]
          scale         <- c.downField("scale").as[Option[Float]]
          caseSensitive <- c.downField("caseSensitive").as[Option[Boolean]]
        } yield {
          RRFOptions(depth.getOrElse(50), scale.getOrElse(60.0))
        }
      )
      given ltrProcessDecoder: Decoder[LTROptions] = Decoder.instance(c =>
        for {
          depth <- c.downField("depth").as[Option[Int]]
          model <- c.downField("model").as[Option[String]]
        } yield {
          LTROptions(depth.getOrElse(50), model.getOrElse("small"))
        }
      )
      given rerankEncoder: Encoder[SuggestRerankOptions] = Encoder.instance {
        case r: RRFOptions => Json.fromJsonObject(JsonObject.fromMap(Map("rrf" -> rrfProcessEncoder(r))))
        case r: LTROptions => Json.fromJsonObject(JsonObject.fromMap(Map("ltr" -> ltrProcessEncoder(r))))
      }
      given rerankDecoder: Decoder[SuggestRerankOptions] = Decoder.instance(c =>
        c.downField("rrf").as[RRFOptions] match {
          case Left(_) =>
            c.downField("ltr").as[LTROptions] match {
              case Right(ltr) => Right(ltr)
              case Left(err)  => Left(DecodingFailure(s"cannot decode rerank process", c.history))
            }
          case Right(rrf) => Right(rrf)
        }
      )
    }

    given suggestRequestEncoder: Encoder[SuggestRequest] = deriveEncoder
    given suggestRequestDecoder: Decoder[SuggestRequest] = Decoder.instance(c =>
      for {
        query   <- c.downField("query").as[String]
        fields  <- c.downField("fields").as[Option[List[String]]]
        count   <- c.downField("count").as[Option[Int]]
        process <- c.downField("rerank").as[Option[SuggestRerankOptions]]
      } yield {
        SuggestRequest(
          query,
          fields.getOrElse(Nil),
          count.getOrElse(10),
          process.getOrElse(SuggestRerankOptions.RRFOptions())
        )
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
