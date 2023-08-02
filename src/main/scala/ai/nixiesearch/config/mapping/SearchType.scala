package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.Language.English
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*

sealed trait SearchType

object SearchType {
  case object NoSearch extends SearchType
  case class SemanticSearch(
      embed: String = "intfloat/e5-base-v2",
      language: Language = English
  ) extends SearchType
  case class LexicalSearch(language: Language = English) extends SearchType

  object json {
    implicit val semanticSearchDecoder: Decoder[SemanticSearch] = deriveDecoder
    implicit val semanticSearchEncoder: Encoder[SemanticSearch] = deriveEncoder
    implicit val lexicalSearchDecoder: Decoder[LexicalSearch]   = deriveDecoder
    implicit val lexicalSearchEncoder: Encoder[LexicalSearch]   = deriveEncoder

    implicit val searchTypeEncoder: Encoder[SearchType] = Encoder.instance {
      case NoSearch          => withType("disabled")
      case s: SemanticSearch => semanticSearchEncoder(s).deepMerge(withType("semantic"))
      case s: LexicalSearch  => lexicalSearchEncoder(s).deepMerge(withType("lexical"))
    }

    implicit val searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(error)       => Left(error)
        case Right("disabled") => Right(NoSearch)
        case Right("lexical")  => lexicalSearchDecoder.tryDecode(c)
        case Right("semantic") => semanticSearchDecoder.tryDecode(c)
        case Right(other)      => Left(DecodingFailure(s"search type '$other' is not supported yet", c.history))
      }
    )

    def withType(tpe: String) = Json.fromJsonObject(JsonObject.fromIterable(List("type" -> Json.fromString(tpe))))
  }

  object yaml {
    implicit val semanticSearchDecoder: Decoder[SemanticSearch] = Decoder.instance(c =>
      for {
        embed <- c.downField("embed").as[Option[String]].map(_.getOrElse(SemanticSearch().embed))
        lang  <- c.downField("language").as[Option[Language]].map(_.getOrElse(SemanticSearch().language))
      } yield {
        SemanticSearch(embed, lang)
      }
    )
    implicit val lexicalSearchDecoder: Decoder[LexicalSearch] = Decoder.instance(c =>
      for {
        lang <- c.downField("language").as[Option[Language]].map(_.getOrElse(SemanticSearch().language))
      } yield {
        LexicalSearch(lang)
      }
    )

    implicit val searchTypeDecoder: Decoder[SearchType] = Decoder.instance(c =>
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
        case Right(other) =>
          Left(DecodingFailure(s"Search type $other is not supported. Try disabled|semantic|lexical", c.history))
      }
    )
  }

}
