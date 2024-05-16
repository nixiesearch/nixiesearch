package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.URL
import ai.nixiesearch.config.mapping.SuggestSchema.{Deduplicate, IndexSteps, SearchSteps}
import io.circe.{Codec, Decoder}
import io.circe.generic.semiauto.*

case class SuggestSchema(index: IndexSteps = IndexSteps(), search: SearchSteps = SearchSteps())

object SuggestSchema {
  case class IndexSteps(lowercase: Boolean = false, expand: Option[Expand] = Some(Expand()))
  case class Expand(minTerms: Int = 1, maxTerms: Int = 3)

  case class SearchSteps(deduplicate: Option[Deduplicate] = Some(Deduplicate()))
  case class Deduplicate(caseSensitive: Boolean = false)

  object yaml {
    given expandDecoder: Decoder[Expand] = Decoder.instance(c =>
      for {
        min <- c.downField("min-terms").as[Option[Int]]
        max <- c.downField("max-terms").as[Option[Int]]
      } yield {
        Expand(min.getOrElse(1), max.getOrElse(3))
      }
    )
    given indexStepsDecoder: Decoder[IndexSteps] = Decoder.instance(c =>
      for {
        lowercase <- c.downField("lowercase").as[Option[Boolean]]
        expand    <- c.downField("expand").as[Option[Expand]]
      } yield {
        IndexSteps(lowercase.getOrElse(false), expand)
      }
    )
    given deduplicateDecoder: Decoder[Deduplicate] = Decoder.instance(c =>
      for {
        caseSensitive <- c.downField("case-sensitive").as[Option[Boolean]]
      } yield {
        Deduplicate(caseSensitive.getOrElse(false))
      }
    )
    given searchDecoder: Decoder[SearchSteps] = Decoder.instance(c =>
      for {
        dedup <- c.downField("deduplicate").as[Option[Deduplicate]]
      } yield {
        SearchSteps(dedup.orElse(Some(Deduplicate())))
      }
    )
    given suggestSchema: Decoder[SuggestSchema] = deriveDecoder
  }

  object json {
    given expandCodec: Codec[Expand]         = deriveCodec
    given indexStepsCodec: Codec[IndexSteps] = deriveCodec

    given deduplicateCodec: Codec[Deduplicate]     = deriveCodec
    given searchStepsCodec: Codec[SearchSteps]     = deriveCodec
    given suggestSchemaCodec: Codec[SuggestSchema] = deriveCodec
  }
}
