package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.SearchResponseFrame.{
  RAGResponseFrame,
  SearchResultsFrame,
  searchResponseFrameEncoder
}
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponseFrame, SuggestRequest}
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.RRFOptions
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.core.aggregate.AggregationResult
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.Searcher
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Request, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.http4s.server.websocket.WebSocketBuilder
import fs2.Stream
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import io.circe.parser.*

case class SearchRoute(searcher: Searcher) extends Route with Logging {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / indexName / "_search" if indexName == searcher.index.name.value =>
      searchBlocking(request)
    case request @ POST -> Root / indexName / "_suggest" if indexName == searcher.index.name.value =>
      suggest(request)
  }

  override def wsroutes(wsb: WebSocketBuilder[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / indexName / "_ping" if indexName == searcher.index.name.value =>
      wsb.build((stream: Stream[IO, WebSocketFrame]) =>
        for {
          text  <- stream.collect { case t: Text => t }
          _     <- Stream.eval(debug(s"ws message: ${text}"))
          frame <- Stream.eval(IO(Text("pong", last = true)))
        } yield {
          frame
        }
      )
    case GET -> Root / indexName / "_search" if indexName == searcher.index.name.value =>
      wsb.build((stream: Stream[IO, WebSocketFrame]) =>
        for {
          text <- stream.collect { case t: Text => t }
          _    <- Stream.eval(debug(s"ws message: ${text}"))
          request <- Stream.eval(IO(decode[SearchRequest](text.str)).flatMap {
            case Left(value)  => IO.raiseError(value)
            case Right(value) => IO.pure(value)
          })
          frame <- searchStreaming(request).map {
            case s: SearchResultsFrame => Text(searchResponseFrameEncoder(s).noSpaces)
            case c: RAGResponseFrame =>
              Text(searchResponseFrameEncoder(c).noSpaces)
          }
          // _ <- Stream.eval(info(s"frame $frame"))
        } yield {
          frame
        }
      )

  }

  def suggest(request: Request[IO]): IO[Response[IO]] = for {
    query    <- request.as[SuggestRequest]
    _        <- info(s"suggest index='${searcher.index.name}' query=$query")
    response <- searcher.suggest(query).flatMap(docs => Ok(docs))
  } yield {
    response
  }

  def searchStreaming(request: SearchRequest): Stream[IO, SearchResponseFrame] = for {
    response <- Stream.eval(searcher.search(request))
    frame <- request.rag match {
      case Some(ragRequest) =>
        Stream.emit(SearchResultsFrame(response)) ++ searcher
          .rag(response.hits, ragRequest, request.id)
          .map(resp => RAGResponseFrame(resp))
      case None => Stream.emit(SearchResultsFrame(response))
    }
  } yield {
    frame
  }

  def searchBlocking(request: Request[IO]): IO[Response[IO]] = for {
    query <- IO(request.entity.length).flatMap {
      case None    => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(0) => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(_) => request.as[SearchRequest]
    }
    _    <- info(s"search index='${searcher.index.name}' query=$query")
    docs <- searcher.search(query)
    response <- query.rag match {
      case None => IO.pure(docs)
      case Some(ragRequest) =>
        searcher
          .rag(docs.hits, ragRequest, query.id)
          .map(_.token)
          .compile
          .fold("")(_ + _)
          .map(resp => docs.copy(response = Some(resp)))
    }
    ok <- Ok(response)
  } yield {
    ok
  }
}

object SearchRoute {
  case class RAGRequest(
      prompt: String,
      model: String,
      topDocs: Int = 10,
      maxDocLength: Int = 128,
      maxResponseLength: Int = 64,
      fields: List[String] = Nil
  )
  object RAGRequest {
    given ragRequestEncoder: Encoder[RAGRequest] = deriveEncoder
    given ragRequestDecoder: Decoder[RAGRequest] = Decoder.instance(c =>
      for {
        prompt            <- c.downField("prompt").as[String]
        model             <- c.downField("model").as[String]
        topDocs           <- c.downField("topDocs").as[Option[Int]]
        maxDocLength      <- c.downField("maxDocLength").as[Option[Int]]
        maxResponseLength <- c.downField("maxResponseLength").as[Option[Int]]
        fields            <- c.downField("fields").as[Option[List[String]]]
      } yield {
        RAGRequest(
          prompt,
          model,
          topDocs.getOrElse(10),
          maxDocLength.getOrElse(128),
          fields = fields.getOrElse(Nil),
          maxResponseLength = maxResponseLength.getOrElse(64)
        )
      }
    )
  }
  case class SearchRequest(
      query: Query,
      filters: Option[Filters] = None,
      size: Int = 10,
      fields: List[String] = Nil,
      aggs: Option[Aggs] = None,
      rag: Option[RAGRequest] = None,
      id: Option[String] = None
  )
  object SearchRequest {
    given searchRequestEncoder: Encoder[SearchRequest] = deriveEncoder
    given searchRequestDecoder: Decoder[SearchRequest] = Decoder.instance(c =>
      for {
        query   <- c.downField("query").as[Option[Query]].map(_.getOrElse(MatchAllQuery()))
        size    <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
        filters <- c.downField("filters").as[Option[Filters]]
        fields <- c.downField("fields").as[Option[List[String]]].map {
          case Some(Nil)  => Nil
          case Some(list) => list
          case None       => Nil
        }
        aggs <- c.downField("aggs").as[Option[Aggs]]
        rag  <- c.downField("rag").as[Option[RAGRequest]]
        id   <- c.downField("id").as[Option[String]]
      } yield {
        SearchRequest(query, filters, size, fields, aggs, rag, id)
      }
    )
  }

  sealed trait SearchResponseFrame
  object SearchResponseFrame {
    case class SearchResultsFrame(value: SearchResponse) extends SearchResponseFrame
    case class RAGResponseFrame(value: RAGResponse)      extends SearchResponseFrame

    given searchResponseFrameEncoder: Encoder[SearchResponseFrame] = Encoder.instance {
      case s: SearchResultsFrame => Json.obj("results" -> SearchResponse.searchResponseEncoder(s.value))
      case r: RAGResponseFrame   => Json.obj("rag" -> RAGResponse.ragResponseEncoder(r.value))
    }
  }

  case class SearchResponse(
      took: Long,
      hits: List[Document],
      aggs: Map[String, AggregationResult],
      response: Option[String] = None,
      id: Option[String] = None,
      ts: Long
  ) {}
  object SearchResponse {
    given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder[SearchResponse].mapJson(_.dropNullValues)
    given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
  }

  import SearchRequest.given

  given searchRequestDecJson: EntityDecoder[IO, SearchRequest]   = jsonOf
  given searchRequestEncJson: EntityEncoder[IO, SearchRequest]   = jsonEncoderOf
  given searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  given searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

  case class ErrorResponse(error: String, cause: Option[String] = None)
  object ErrorResponse {
    given errorResponseEncoder: Encoder[ErrorResponse]        = deriveEncoder[ErrorResponse].mapJson(_.dropNullValues)
    given errorResponseDecoder: Decoder[ErrorResponse]        = deriveDecoder
    given errorResponseJson: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf
    given errorResponseDecoderJson: EntityDecoder[IO, ErrorResponse] = jsonOf

    def apply(e: Throwable) = Option(e.getCause) match {
      case None        => new ErrorResponse(e.getMessage)
      case Some(cause) => new ErrorResponse(e.getMessage, Some(cause.getMessage))
    }
  }

  case class RAGResponse(id: Option[String], token: String, ts: Long, took: Long, last: Boolean)
  object RAGResponse {
    given ragResponseEncoder: Encoder[RAGResponse] = deriveEncoder[RAGResponse].mapJson(_.dropNullValues)
    given ragResponseDecoder: Decoder[RAGResponse] = deriveDecoder
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
