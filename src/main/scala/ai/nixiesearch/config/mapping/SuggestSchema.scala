package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.URL
import ai.nixiesearch.config.mapping.SuggestSchema.{Expand, Lemmatize}
import io.circe.{Codec, Decoder}
import io.circe.generic.semiauto.*

// todo: add lemmatization, reranking and re-weighting
case class SuggestSchema(
    lowercase: Boolean = false,
    expand: Option[Expand] = Some(Expand()),
    lemmatize: Option[Lemmatize] = None
)

object SuggestSchema {
  case class Expand(minTerms: Int = 1, maxTerms: Int = 3)
  case class Lemmatize(dictionary: URL)

  object yaml {
    given expandDecoder: Decoder[Expand] = Decoder.instance(c =>
      for {
        min <- c.downField("min-terms").as[Option[Int]]
        max <- c.downField("max-terms").as[Option[Int]]
      } yield {
        Expand(min.getOrElse(1), max.getOrElse(3))
      }
    )
    given lemmatizeDecoder: Decoder[Lemmatize] = Decoder.instance(c =>
      for {
        dic <- c.downField("dictionary").as[URL]
      } yield {
        Lemmatize(dic)
      }
    )
    given suggestSchema: Decoder[SuggestSchema] = Decoder.instance(c =>
      for {
        lowercase <- c.downField("lowercase").as[Option[Boolean]]
        expand    <- c.downField("expand").as[Option[Expand]]
        lemmatize <- c.downField("lemmatize").as[Option[Lemmatize]]
      } yield {
        SuggestSchema(lowercase.getOrElse(false), expand.orElse(Some(Expand())), lemmatize)
      }
    )
  }

  object json {
    given expandCodec: Codec[Expand]               = deriveCodec
    given lemmatizeCodec: Codec[Lemmatize]         = deriveCodec
    given suggestSchemaCodec: Codec[SuggestSchema] = deriveCodec
  }
}
