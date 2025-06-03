package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.SearchParams.Distance.{Cosine, Dot}
import ai.nixiesearch.config.mapping.SearchParams.QuantStore.Float32
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticParams}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

import scala.util.{Failure, Success}

case class SearchParams(lexical: Option[LexicalParams] = None, semantic: Option[SemanticParams] = None)

object SearchParams {
  case class LexicalParams(analyze: Language = Language.Generic)

  case class SemanticParams(
      model: ModelRef,
      ef: Int = 32,
      m: Int = 16,
      workers: Int = Runtime.getRuntime.availableProcessors(),
      quantize: QuantStore = Float32,
      distance: Distance = Dot
  )

  enum QuantStore(val alias: String) {
    case Float32 extends QuantStore("float32")
    case Int8    extends QuantStore("int8")
    case Int4    extends QuantStore("int4")
    case Int1    extends QuantStore("int1")
  }

  given quantStoreEncoder: Encoder[QuantStore] = Encoder.encodeString.contramap(_.alias)

  given quantStoreDecoder: Decoder[QuantStore] = Decoder.decodeString.emapTry {
    case QuantStore.Float32.alias => Success(QuantStore.Float32)
    case QuantStore.Int8.alias    => Success(QuantStore.Int8)
    case QuantStore.Int4.alias    => Success(QuantStore.Int4)
    case QuantStore.Int1.alias    => Success(QuantStore.Int1)
    case other => Failure(UserError(s"cannot decode quant method '$other': we support int1/int4/int8/float32"))
  }
  enum Distance(val alias: String) {
    case Cosine extends Distance("cosine")
    case Dot    extends Distance("dot")
  }
  given distanceEncoder: Encoder[Distance] = Encoder.encodeString.contramap(_.alias)
  given distanceDecoder: Decoder[Distance] = Decoder.decodeString.emapTry {
    case Distance.Cosine.alias => Success(Cosine)
    case Distance.Dot.alias    => Success(Dot)
    case other                 => Failure(UserError(s"cannot decode distance function '$other': we support cosine/dot"))
  }
  given embeddingSearchParamsEncoder: Encoder[SemanticParams] = deriveEncoder
  given embeddingSearchParamsDecoder: Decoder[SemanticParams] = Decoder.instance(c =>
    for {
      model    <- c.downField("model").as[ModelRef]
      ef       <- c.downField("ef").as[Option[Int]]
      m        <- c.downField("m").as[Option[Int]]
      workers  <- c.downField("workers").as[Option[Int]]
      quantize <- c.downField("quantize").as[Option[QuantStore]]
      distance <- c.downField("distance").as[Option[Distance]]
    } yield {
      SemanticParams(
        model = model,
        ef = ef.getOrElse(32),
        m = m.getOrElse(16),
        workers = workers.getOrElse(Runtime.getRuntime.availableProcessors()),
        quantize = quantize.getOrElse(Float32),
        distance = distance.getOrElse(Dot)
      )
    }
  )

  given lexicalSearchParamsEncoder: Encoder[LexicalParams] = deriveEncoder
  given lexicalSearchParamsDecoder: Decoder[LexicalParams] = Decoder.instance(c =>
    for {
      analyze <- c.downField("analyze").as[Option[Language]]
    } yield {
      LexicalParams(analyze = analyze.getOrElse(Language.Generic))
    }
  )
  given searchTypeEncoder: Encoder[SearchParams] = deriveEncoder
  given searchTypeDecoder: Decoder[SearchParams] = Decoder.instance(c =>
    c.as[Boolean] match {
      case Left(_) =>
        for {
          lexical  <- c.downField("lexical").as[Option[LexicalParams]]
          semantic <- c.downField("semantic").as[Option[SemanticParams]]
          _ <- c.downField("type").as[Option[String]] match {
            case Left(err)        => Left(err)
            case Right(None)      => Right({})
            case Right(Some(tpe)) => Left(DecodingFailure(s"you use old search options format", c.history))
          }
        } yield {
          SearchParams(lexical, semantic)
        }
      case Right(false) => Right(SearchParams(None, None))
      case Right(true) =>
        Left(DecodingFailure(s"unexpected search settings 'true': please set lexical/semantic params", c.history))
    }
  )

}
