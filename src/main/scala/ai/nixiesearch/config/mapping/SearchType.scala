package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

sealed trait SearchType

object SearchType {
  sealed trait SemanticSearchLikeType extends SearchType {
    def model: ModelRef
  }

  case object NoSearch extends SearchType

  case class SemanticSearch(model: ModelRef) extends SearchType with SemanticSearchLikeType

  case class LexicalSearch() extends SearchType

  case class HybridSearch(model: ModelRef) extends SearchType with SemanticSearchLikeType

  object SemanticSearchLikeType {
    def unapply(tpe: SearchType): Option[ModelRef] = tpe match {
      case NoSearch              => None
      case SemanticSearch(model) => Some(model)
      case LexicalSearch()       => None
      case HybridSearch(model)   => Some(model)
    }
  }

  case class ModelPrefix(query: String = "", document: String = "")

  object json {
    given modelPrefixCodec: Codec[ModelPrefix]       = deriveCodec
    given semanticSearchCodec: Codec[SemanticSearch] = deriveCodec
    given lexicalSearchCodec: Codec[LexicalSearch]   = deriveCodec
    given hybridSearchCodec: Codec[HybridSearch]     = deriveCodec

    given searchTypeEncoder: Encoder[SearchType] = Encoder.instance {
      case NoSearch          => withType("disabled")
      case s: HybridSearch   => hybridSearchCodec(s).deepMerge(withType("hybrid"))
      case s: SemanticSearch => semanticSearchCodec(s).deepMerge(withType("semantic"))
      case s: LexicalSearch  => lexicalSearchCodec(s).deepMerge(withType("lexical"))
    }

    given searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(error)       => Left(error)
        case Right("disabled") => Right(NoSearch)
        case Right("lexical")  => lexicalSearchCodec.tryDecode(c)
        case Right("semantic") => semanticSearchCodec.tryDecode(c)
        case Right("hybrid")   => hybridSearchCodec.tryDecode(c)
        case Right(other)      => Left(DecodingFailure(s"search type '$other' is not supported yet", c.history))
      }
    )

    def withType(tpe: String) = Json.fromJsonObject(JsonObject.fromIterable(List("type" -> Json.fromString(tpe))))
  }

  object yaml {
    given modelPrefixDecoder: Decoder[ModelPrefix] = Decoder.instance(c =>
      for {
        query    <- c.downField("query").as[Option[String]].map(_.getOrElse(""))
        document <- c.downField("document").as[Option[String]].map(_.getOrElse(""))
      } yield {
        ModelPrefix(query, document)
      }
    )
    given semanticSearchDecoder: Decoder[SemanticSearch] = deriveDecoder

    given hybridSearchDecoder: Decoder[HybridSearch] = deriveDecoder

    given lexicalSearchDecoder: Decoder[LexicalSearch] = Decoder.const(LexicalSearch())

    given searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(value) =>
          Left(
            DecodingFailure(
              s"cannot decode search field type: ${value}",
              c.history
            )
          )
        case Right("false" | "off" | "disabled") => Right(NoSearch)
        case Right("semantic")                   => semanticSearchDecoder.tryDecode(c)
        case Right("lexical")                    => lexicalSearchDecoder.tryDecode(c)
        case Right("hybrid")                     => hybridSearchDecoder.tryDecode(c)
        case Right(other) =>
          Left(
            DecodingFailure(s"Search type $other is not supported. Try disabled|semantic|lexical", c.history)
          )
      }
    )
  }

}
