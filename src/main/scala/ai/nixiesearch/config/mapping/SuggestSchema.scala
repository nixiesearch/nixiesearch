package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.URL
import ai.nixiesearch.config.mapping.SuggestSchema.Expand
import io.circe.{Codec, Decoder}
import io.circe.generic.semiauto.*

// todo: add lemmatization, reranking and re-weighting
case class SuggestSchema(
    analyze: Language = Language.Generic,
    expand: Option[Expand] = Some(Expand())
)

object SuggestSchema {
  case class Expand(minTerms: Int = 1, maxTerms: Int = 3)

  object yaml {
    given expandDecoder: Decoder[Expand] = Decoder.instance(c =>
      for {
        min <- c.downField("min-terms").as[Option[Int]]
        max <- c.downField("max-terms").as[Option[Int]]
      } yield {
        Expand(min.getOrElse(1), max.getOrElse(3))
      }
    )
    given suggestSchema: Decoder[SuggestSchema] = Decoder.instance(c =>
      for {
        analyze <- c.downField("analyze").as[Option[Language]]
        expand  <- c.downField("expand").as[Option[Expand]]
      } yield {
        SuggestSchema(analyze = analyze.getOrElse(Language.Generic), expand.orElse(Some(Expand())))
      }
    )
  }

  object json {
    given expandCodec: Codec[Expand]               = deriveCodec
    given suggestSchemaCodec: Codec[SuggestSchema] = deriveCodec
  }
}
