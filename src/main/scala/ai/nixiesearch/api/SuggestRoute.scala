package ai.nixiesearch.api

import ai.nixiesearch.api.SuggestRoute.Deduplication.DedupThreshold
import ai.nixiesearch.api.SuggestRoute.{Deduplication, SuggestRequest, SuggestResponse}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.mapping.SuggestMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.search.Suggester
import ai.nixiesearch.index.IndexRegistry
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.lucene.search.KnnFloatVectorQuery

case class SuggestRoute(indices: IndexRegistry) extends Route with Logging {
  val routes = HttpRoutes.of[IO] { case request @ POST -> Root / indexName / "_suggest" =>
    indices.index(indexName).flatMap {
      case Some(index) =>
        for {
          query    <- request.as[SuggestRequest]
          _        <- info(s"POST /$indexName/_suggest query=$query")
          response <- suggest(indexName, query)
        } yield {
          response
        }
      case None => NotFound(s"index $indexName is missing")
    }
  }

  val OVERFETCH = 5

  def suggest(indexName: String, query: SuggestRequest): IO[Response[IO]] = for {
    index <- indices.index(indexName).flatMap {
      case Some(value) => IO.pure(value)
      case None        => IO.raiseError(new Exception(s"index $indexName not found"))
    }
    mapping <- index.mappingRef.get
    schema <- IO.fromOption(mapping.fields.get(SuggestMapping.SUGGEST_FIELD))(
      new Exception(s"field ${SuggestMapping.SUGGEST_FIELD} missing")
    )
    embed <- schema match {
      case TextFieldSchema(_, SemanticSearch(handle, _), _, _, _, _) =>
        for {
          model   <- index.encoders.get(handle)
          encoded <- IO(model.embed(Array(query.text)))
          head    <- IO.fromOption(encoded.headOption)(new Exception("encoder returned zero results"))
        } yield {
          head
        }
      case _ => IO.raiseError(new Exception(s"suggest field has wrong schema: $schema"))
    }
    knnquery <- IO(new KnnFloatVectorQuery(SuggestMapping.SUGGEST_FIELD, embed, query.size * OVERFETCH))
    threshold = query.deduplication match {
      case DedupThreshold(threshold) => threshold
      case Deduplication.NoDedup     => 1.0
    }
    response <- Suggester.suggest(index, knnquery, query.size * OVERFETCH, query.size, threshold)
    ok       <- Ok(response)
  } yield {
    ok
  }
}

object SuggestRoute {
  sealed trait Deduplication
  object Deduplication {
    case class DedupThreshold(threshold: Double) extends Deduplication
    case object NoDedup                          extends Deduplication

    given dedupEncoder: Encoder[Deduplication] = Encoder.instance {
      case DedupThreshold(threshold) => Json.obj("threshold" -> Json.fromDoubleOrNull(threshold))
      case NoDedup                   => Json.fromString("false")
    }

    given dedupDecoder: Decoder[Deduplication] = Decoder.instance(c =>
      c.as[String] match {
        case Right("false") => Right(NoDedup)
        case Right(other) =>
          Left(DecodingFailure(s"cannot decode deduplication field. expected 'false'|obj, got '$other'", c.history))
        case Left(err1) =>
          c.downField("threshold").as[Double] match {
            case Left(err2) =>
              Left(
                DecodingFailure(s"cannot decode deduplication field. expected 'false'|obj - $err1 - $err2'", c.history)
              )
            case Right(value) => Right(DedupThreshold(value))
          }
      }
    )
  }

  case class SuggestRequest(text: String, size: Int = 10, deduplication: Deduplication = DedupThreshold(0.95))
  case class SuggestResponse(suggestions: List[Suggestion])
  case class Suggestion(text: String, score: Float, forms: List[SuggestionForm])
  case class SuggestionForm(text: String, score: Float)

  given suggestionFormCodec: Codec[SuggestionForm]     = deriveCodec
  given suggestionCodec: Codec[Suggestion]             = deriveCodec
  given suggestRequestEncoder: Encoder[SuggestRequest] = deriveEncoder
  given suggestRequestDecoder: Decoder[SuggestRequest] = Decoder.instance(c =>
    for {
      text  <- c.downField("text").as[String]
      size  <- c.downField("size").as[Option[Int]]
      dedup <- c.downField("deduplication").as[Option[Deduplication]]
    } yield {
      SuggestRequest(
        text = text,
        size = size.getOrElse(10),
        deduplication = dedup.getOrElse(DedupThreshold(0.95))
      )
    }
  )
  given suggestResponseCodec: Codec[SuggestResponse] = deriveCodec

  given suggestRequestDecJson: EntityDecoder[IO, SuggestRequest] = jsonOf
  given suggestRequestEncJson: EntityEncoder[IO, SuggestRequest] = jsonEncoderOf

  given suggestResponseEncJson: EntityEncoder[IO, SuggestResponse] = jsonEncoderOf
  given suggestResponseDecJson: EntityDecoder[IO, SuggestResponse] = jsonOf

}
