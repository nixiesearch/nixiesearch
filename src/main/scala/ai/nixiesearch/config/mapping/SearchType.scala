package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.SearchType.QuantStore.Float32
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearchParams, LexicalSearchParams}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

import scala.util.{Failure, Success}

case class SearchType(lexical: Option[LexicalSearchParams] = None, semantic: Option[SemanticSearchParams] = None)

object SearchType {
  case class LexicalSearchParams(analyze: Language = Language.Generic)

  case class SemanticSearchParams(
      model: ModelRef,
      ef: Int = 10,
      m: Int = 10,
      workers: Int = Runtime.getRuntime.availableProcessors(),
      quantize: QuantStore = Float32
  )

  enum QuantStore(val alias: String) {
    case Float32 extends QuantStore("float32")
    case Int8    extends QuantStore("int8")
    case Int4    extends QuantStore("int4")
  }

  given quantStoreEncoder: Encoder[QuantStore] = Encoder.encodeString.contramap(_.alias)

  given quantStoreDecoder: Decoder[QuantStore] = Decoder.decodeString.emapTry {
    case QuantStore.Float32.alias => Success(QuantStore.Float32)
    case QuantStore.Int8.alias    => Success(QuantStore.Int8)
    case QuantStore.Int4.alias    => Success(QuantStore.Int4)
    case other                    => Failure(UserError(s"cannot decode quant method $other"))
  }
  given embeddingSearchParamsEncoder: Encoder[SemanticSearchParams] = deriveEncoder
  given embeddingSearchParamsDecoder: Decoder[SemanticSearchParams] = Decoder.instance(c =>
    for {
      model    <- c.downField("model").as[ModelRef]
      ef       <- c.downField("ef").as[Option[Int]]
      m        <- c.downField("m").as[Option[Int]]
      workers  <- c.downField("workers").as[Option[Int]]
      quantize <- c.downField("quantize").as[Option[QuantStore]]
    } yield {
      SemanticSearchParams(
        model = model,
        ef = ef.getOrElse(32),
        m = m.getOrElse(16),
        workers = workers.getOrElse(Runtime.getRuntime.availableProcessors()),
        quantize = quantize.getOrElse(Float32)
      )
    }
  )

  given lexicalSearchParamsEncoder: Encoder[LexicalSearchParams] = deriveEncoder
  given lexicalSearchParamsDecoder: Decoder[LexicalSearchParams] = Decoder.instance(c =>
    for {
      analyze <- c.downField("analyze").as[Option[Language]]
    } yield {
      LexicalSearchParams(analyze = analyze.getOrElse(Language.Generic))
    }
  )
  given searchTypeEncoder: Encoder[SearchType] = deriveEncoder
  given searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
    c.as[Boolean] match {
      case Left(_) =>
        for {
          lexical  <- c.downField("lexical").as[Option[LexicalSearchParams]]
          semantic <- c.downField("semantic").as[Option[SemanticSearchParams]]
        } yield {
          SearchType(lexical, semantic)
        }
      case Right(false) => Right(SearchType(None, None))
      case Right(true) =>
        Left(DecodingFailure(s"unexpected search settings 'true': please set lexical/semantic params", c.history))
    }
  )

}
