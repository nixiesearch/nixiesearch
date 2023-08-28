package ai.nixiesearch.api

import ai.nixiesearch.api.SuggestRoute.{SuggestRequest, SuggestResponse}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.mapping.SuggestMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexRegistry
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import org.apache.lucene.search.KnnFloatVectorQuery

case class SuggestRoute(indices: IndexRegistry) extends Route with Logging {
  val routes = HttpRoutes.of[IO] { case request @ POST -> Root / indexName / "_suggest" =>
    indices.reader(indexName).flatMap {
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

  def suggest(index: String, query: SuggestRequest): IO[Response[IO]] = for {
    mapping <- indices.mapping(index).flatMap {
      case Some(value) => IO.pure(value)
      case None        => IO.raiseError(new Exception(s"index $index not found"))
    }
    reader <- indices.reader(index).flatMap {
      case Some(value) => IO.pure(value)
      case None        => IO.raiseError(new Exception(s"index $index not found"))
    }
    schema <- IO.fromOption(mapping.fields.get(SuggestMapping.SUGGEST_FIELD))(
      new Exception(s"field ${SuggestMapping.SUGGEST_FIELD} missing")
    )
    embed <- schema match {
      case TextFieldSchema(_, SemanticSearch(handle, _), _, _, _, _) =>
        for {
          model   <- IO.fromOption(reader.encoders.get(handle))(new Exception(s"model $handle not found in cache"))
          encoded <- IO(model.embed(Array(query.text)))
          head    <- IO.fromOption(encoded.headOption)(new Exception("encoder returned zero results"))
        } yield {
          head
        }
      case _ => IO.raiseError(new Exception(s"suggest field has wrong schema: $schema"))
    }
    knnquery <- IO(new KnnFloatVectorQuery(SuggestMapping.SUGGEST_FIELD, embed, query.size))
    response <- reader.suggest(knnquery, query.size)
    ok       <- Ok(response)
  } yield {
    ok
  }
}

object SuggestRoute {
  case class SuggestRequest(text: String, size: Int)
  case class SuggestResponse(suggestions: List[Suggestion])
  case class Suggestion(text: String, score: Float)

  given suggestionCodec: Codec[Suggestion]           = deriveCodec
  given suggestRequestCodec: Codec[SuggestRequest]   = deriveCodec
  given suggestResponseCodec: Codec[SuggestResponse] = deriveCodec

  given suggestRequestDecJson: EntityDecoder[IO, SuggestRequest] = jsonOf
  given suggestRequestEncJson: EntityEncoder[IO, SuggestRequest] = jsonEncoderOf

  given suggestResponseEncJson: EntityEncoder[IO, SuggestResponse] = jsonEncoderOf
  given suggestResponseDecJson: EntityDecoder[IO, SuggestResponse] = jsonOf

}
