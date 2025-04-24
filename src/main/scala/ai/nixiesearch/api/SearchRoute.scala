package ai.nixiesearch.api

import ai.nixiesearch.api.SearchRoute.SearchResponseFrame.{RAGResponseFrame, SearchResultsFrame}
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.Last
import ai.nixiesearch.api.SearchRoute.SortPredicate.{MissingValue, SortOrder}
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.{ASC, Default}
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse, SearchResponseFrame, SuggestRequest}
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.RRFOptions
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.aggregate.AggregationResult
import ai.nixiesearch.core.metrics.{Metrics, SearchMetrics}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.Searcher
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject, syntax}
import org.http4s.{
  Charset,
  Entity,
  EntityDecoder,
  EntityEncoder,
  Headers,
  HttpRoutes,
  MediaType,
  Request,
  Response,
  Status
}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import fs2.Stream
import io.circe.syntax.*
import org.http4s.headers.`Content-Type`
import io.circe.syntax.*
import io.prometheus.metrics.model.registry.PrometheusRegistry

import scala.util.{Failure, Success}

case class SearchRoute(searcher: Searcher) extends Route with Logging {
  given documentCodec: Codec[Document]                 = Document.codecFor(searcher.index.mapping)
  given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder[SearchResponse].mapJson(_.dropNullValues)
  given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
  given searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  given searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "v1" / "index" / indexName / "search"
        if searcher.index.mapping.nameMatches(indexName) =>
      search(request)

    case request @ POST -> Root / "v1" / "index" / indexName / "suggest"
        if searcher.index.mapping.nameMatches(indexName) =>
      suggest(request)

    // legacy
    case request @ POST -> Root / indexName / "_search" if searcher.index.mapping.nameMatches(indexName) =>
      search(request)

    case request @ POST -> Root / indexName / "_suggest" if searcher.index.mapping.nameMatches(indexName) =>
      suggest(request)
  }

  def search(request: Request[IO]): IO[Response[IO]] = for {
    request <- IO(request.entity.length).flatMap {
      case None    => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(0) => IO.pure(SearchRequest(query = MatchAllQuery()))
      case Some(_) => request.as[SearchRequest]
    }
    response <- searchBlocking(request)
  } yield {
    Response[IO](
      status = Status.Ok,
      headers = Headers(`Content-Type`(new MediaType("application", "json"))),
      entity = Entity.string(response.asJson.noSpaces, Charset.`UTF-8`)
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
          .rag(response.hits, ragRequest)
          .map(resp => RAGResponseFrame(resp))
      case None => Stream.emit(SearchResultsFrame(response))
    }
  } yield {
    frame
  }

  def searchBlocking(request: SearchRequest): IO[SearchResponse] = for {
    _    <- info(s"search index='${searcher.index.name}' query=$request")
    docs <- searcher.search(request)
    response <- request.rag match {
      case None =>
        IO.pure(docs)
      case Some(ragRequest) =>
        searcher
          .rag(docs.hits, ragRequest)
          .map(_.token)
          .compile
          .fold("")(_ + _)
          .map(resp => docs.copy(response = Some(resp)))
    }

  } yield {
    response
  }

}

object SearchRoute {
  case class RAGRequest(
      prompt: String,
      model: ModelRef,
      topDocs: Int = 10,
      maxDocLength: Int = 512,
      maxResponseLength: Int = 64,
      fields: List[FieldName] = Nil,
      stream: Boolean = false
  )
  object RAGRequest {
    given ragRequestEncoder: Encoder[RAGRequest] = deriveEncoder
    given ragRequestDecoder: Decoder[RAGRequest] = Decoder.instance(c =>
      for {
        prompt            <- c.downField("prompt").as[String]
        model             <- c.downField("model").as[ModelRef]
        topDocs           <- c.downField("topDocs").as[Option[Int]]
        maxDocLength      <- c.downField("maxDocLength").as[Option[Int]]
        maxResponseLength <- c.downField("maxResponseLength").as[Option[Int]]
        fields            <- c.downField("fields").as[Option[List[FieldName]]]
        stream            <- c.downField("stream").as[Option[Boolean]]
      } yield {
        RAGRequest(
          prompt,
          model,
          topDocs.getOrElse(10),
          maxDocLength.getOrElse(128),
          fields = fields.getOrElse(Nil),
          maxResponseLength = maxResponseLength.getOrElse(64),
          stream = stream.getOrElse(false)
        )
      }
    )
  }
  case class SearchRequest(
      query: Query,
      filters: Option[Filters] = None,
      size: Int = 10,
      fields: List[FieldName] = Nil,
      aggs: Option[Aggs] = None,
      rag: Option[RAGRequest] = None,
      sort: List[SortPredicate] = Nil
  )
  object SearchRequest {
    given searchRequestEncoder: Encoder[SearchRequest] = deriveEncoder
    given searchRequestDecoder: Decoder[SearchRequest] = Decoder.instance(c =>
      for {
        query   <- c.downField("query").as[Option[Query]].map(_.getOrElse(MatchAllQuery()))
        size    <- c.downField("size").as[Option[Int]].map(_.getOrElse(10))
        filters <- c.downField("filters").as[Option[Filters]]
        fields <- c.downField("fields").as[Option[List[FieldName]]].map {
          case Some(Nil)  => Nil
          case Some(list) => list
          case None       => Nil
        }
        aggs <- c.downField("aggs").as[Option[Aggs]]
        rag  <- c.downField("rag").as[Option[RAGRequest]]
        sort <- c.downField("sort").as[Option[List[SortPredicate]]]
      } yield {
        SearchRequest(query, filters, size, fields, aggs, rag, sort.getOrElse(Nil))
      }
    )
  }

  sealed trait SearchResponseFrame {
    def asServerSideEvent(using Encoder[SearchResponse]): String
  }
  object SearchResponseFrame {
    case class SearchResultsFrame(value: SearchResponse) extends SearchResponseFrame {
      override def asServerSideEvent(using Encoder[SearchResponse]): String =
        s"event: results\ndata: ${value.asJson.noSpaces}\n\n"
    }
    case class RAGResponseFrame(value: RAGResponse) extends SearchResponseFrame {
      override def asServerSideEvent(using Encoder[SearchResponse]): String =
        s"event: rag\ndata: ${value.asJson.noSpaces}\n\n"
    }

  }

  case class SearchResponse(
      took: Long,
      hits: List[Document],
      aggs: Map[String, AggregationResult],
      response: Option[String] = None,
      ts: Long
  ) {}

  import SearchRequest.given

  given searchRequestDecJson: EntityDecoder[IO, SearchRequest] = jsonOf
  given searchRequestEncJson: EntityEncoder[IO, SearchRequest] = jsonEncoderOf

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

  case class RAGResponse(token: String, ts: Long, took: Long, last: Boolean)
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

  sealed trait SortPredicate {
    def field: FieldName
    // def missing: MissingValue
  }

  object SortPredicate {
    sealed trait SortOrder
    object SortOrder {
      case object ASC     extends SortOrder
      case object DESC    extends SortOrder
      case object Default extends SortOrder

      given sortOrderEncoder: Encoder[SortOrder] = Encoder.encodeString.contramap {
        case ASC     => "asc"
        case DESC    => "desc"
        case Default => "default"
      }

      given sortOrderDecoder: Decoder[SortOrder] = Decoder.decodeString.map(_.toLowerCase).emapTry {
        case "asc"     => Success(SortOrder.ASC)
        case "desc"    => Success(SortOrder.DESC)
        case "default" => Success(SortOrder.Default)
        case other     => Failure(UserError(s"sorting ordering '$other' not supported, try asc/desc/default"))
      }
    }

    sealed trait MissingValue
    object MissingValue {
      case object First extends MissingValue
      case object Last  extends MissingValue

      def of[T](min: T, max: T, reverse: Boolean, missingValue: MissingValue): T = (missingValue, reverse) match {
        case (First, false) => min
        case (Last, false)  => max
        case (First, true)  => max
        case (Last, true)   => min
      }

      given missingValueEncoder: Encoder[MissingValue] = Encoder.encodeString.contramap {
        case MissingValue.First => "first"
        case MissingValue.Last  => "last"
      }

      given missingValueDecoder: Decoder[MissingValue] = Decoder.decodeString.map(_.toLowerCase).emapTry {
        case "first" => Success(MissingValue.First)
        case "last"  => Success(MissingValue.Last)
        case other   => Failure(UserError(s"missing value type '$other' not supported, try first/last"))
      }
    }
    case class FieldValueSort(
        field: FieldName,
        order: SortOrder = SortOrder.Default,
        missing: MissingValue = MissingValue.Last
    ) extends SortPredicate

    case class DistanceSort(
        field: FieldName,
        lat: Double,
        lon: Double
    ) extends SortPredicate

    given sortPredicateEncoder: Encoder[SortPredicate] = Encoder.instance {
      case sort: FieldValueSort =>
        Json.obj(
          sort.field.name -> Json.obj(
            "order"   -> SortOrder.sortOrderEncoder(sort.order),
            "missing" -> MissingValue.missingValueEncoder(sort.missing)
          )
        )
      case sort: DistanceSort =>
        Json.obj(
          sort.field.name -> Json.obj(
            "lat" -> Json.fromDoubleOrNull(sort.lat),
            "lon" -> Json.fromDoubleOrNull(sort.lon)
          )
        )
    }

    given sortPredicateDecoder: Decoder[SortPredicate] = Decoder.instance { c =>
      {
        c.focus match {
          case None => Left(DecodingFailure("sort object cannot be empty", c.history))
          case Some(json) =>
            json.fold(
              jsonNull = Left(DecodingFailure("sort object cannot be null", c.history)),
              jsonBoolean = _ => Left(DecodingFailure("sort object cannot be boolean", c.history)),
              jsonNumber = _ => Left(DecodingFailure("sort object cannot be a number", c.history)),
              jsonString = str => Right(FieldValueSort(StringName(str))),
              jsonArray = _ => Left(DecodingFailure("sort object cannot be a list", c.history)),
              jsonObject = obj =>
                obj.keys.toList match {
                  case field :: Nil =>
                    for {
                      orderOption   <- c.downField(field).downField("order").as[Option[SortOrder]]
                      missingOption <- c.downField(field).downField("missing").as[Option[MissingValue]]
                      latOption     <- c.downField(field).downField("lat").as[Option[Double]]
                      lonOption     <- c.downField(field).downField("lon").as[Option[Double]]
                      sort <- (orderOption, missingOption, latOption, lonOption) match {
                        case (None, None, Some(lat), Some(lon)) => Right(DistanceSort(StringName(field), lat, lon))
                        case (None, Some(m), Some(_), Some(_)) =>
                          Left(DecodingFailure(s"distance sort cannot have a 'missing' option, but got $m", c.history))
                        case (Some(o), _, Some(_), Some(_)) =>
                          Left(DecodingFailure(s"distance sort cannot have an 'order' option, but got $o", c.history))
                        case (order, missing, None, None) =>
                          Right(FieldValueSort(StringName(field), order.getOrElse(Default), missing.getOrElse(Last)))
                        case _ => Left(DecodingFailure(s"cannot decode sort predicate $obj", c.history))
                      }
                    } yield {
                      sort
                    }
                  case field :: tail =>
                    Left(DecodingFailure(s"sort object cannot contain multiple keys $field and $tail", c.history))
                  case Nil => Left(DecodingFailure("sort object cannot be nil", c.history))
                }
            )
        }

      }
    }
  }
}
