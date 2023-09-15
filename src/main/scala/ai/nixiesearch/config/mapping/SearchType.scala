package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

sealed trait SearchType

object SearchType {
  sealed trait SemanticSearchLikeType extends SearchType {
    def model: ModelHandle
    def prefix: ModelPrefix
  }

  case object NoSearch extends SearchType

  case class SemanticSearch(
      model: ModelHandle = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
      prefix: ModelPrefix = ModelPrefix.e5
  ) extends SearchType
      with SemanticSearchLikeType

  case class LexicalSearch(language: Language = English) extends SearchType

  case class HybridSearch(
      model: ModelHandle = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx"),
      prefix: ModelPrefix = ModelPrefix.e5,
      language: Language = English
  ) extends SearchType
      with SemanticSearchLikeType

  object SemanticSearchLikeType {
    def unapply(tpe: SearchType): Option[(ModelHandle, ModelPrefix)] = tpe match {
      case NoSearch                              => None
      case SemanticSearch(model, prefix)         => Some((model, prefix))
      case LexicalSearch(language)               => None
      case HybridSearch(model, prefix, language) => Some((model, prefix))
    }
  }

  object LexicalSearchLike {
    def unapply(tpe: SearchType): Option[Language] = tpe match {
      case NoSearch                              => None
      case SemanticSearch(model, prefix)         => None
      case LexicalSearch(language)               => Some(language)
      case HybridSearch(model, prefix, language) => Some(language)
    }
  }

  case class ModelPrefix(query: String = "", document: String = "")
  object ModelPrefix {
    val e5 = ModelPrefix("query: ", "passage: ")
  }

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
    given semanticSearchDecoder: Decoder[SemanticSearch] = Decoder.instance(c =>
      for {
        model <- c.downField("model").as[Option[ModelHandle]].map(_.getOrElse(SemanticSearch().model))
        prefix <- c
          .downField("prefix")
          .as[Option[ModelPrefix]]
          .map(_.getOrElse(model.name match {
            case e5 if e5.contains("e5") => ModelPrefix.e5
            case _                       => ModelPrefix()
          }))
      } yield {
        SemanticSearch(model, prefix)
      }
    )

    given hybridSearchDecoder: Decoder[HybridSearch] = Decoder.instance(c =>
      for {
        model <- c.downField("model").as[Option[ModelHandle]].map(_.getOrElse(SemanticSearch().model))
        prefix <- c
          .downField("prefix")
          .as[Option[ModelPrefix]]
          .map(_.getOrElse(model.name match {
            case e5 if e5.contains("e5") => ModelPrefix.e5
            case _                       => ModelPrefix()
          }))
        lang <- c.downField("language").as[Option[Language]].map(_.getOrElse(LexicalSearch().language))
      } yield {
        HybridSearch(model, prefix, lang)
      }
    )

    given lexicalSearchDecoder: Decoder[LexicalSearch] = Decoder.instance(c =>
      for {
        lang <- c.downField("language").as[Option[Language]].map(_.getOrElse(LexicalSearch().language))
      } yield {
        LexicalSearch(lang)
      }
    )

    given searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
      c.as[String] match {
        case Left(_) =>
          c.as[Boolean] match {
            case Left(value) =>
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

            case Right(false) => Right(NoSearch)
            case Right(true)  => Right(SemanticSearch())
          }
        case Right("false" | "off" | "disabled") => Right(NoSearch)
        case Right("semantic")                   => Right(SemanticSearch())
        case Right("lexical")                    => Right(LexicalSearch())
        case Right("hybrid")                     => Right(LexicalSearch())
        case Right(other) =>
          Left(DecodingFailure(s"Search type $other is not supported. Try disabled|semantic|lexical", c.history))
      }
    )
  }

}
