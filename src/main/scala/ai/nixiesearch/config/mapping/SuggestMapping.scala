package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.IndexMapping.yaml.decodeAlias
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.mapping.SuggestMapping.{SUGGEST_FIELD, Transform}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.Decoder
import io.circe.generic.semiauto.*

case class SuggestMapping(
    name: String,
    alias: List[Alias] = Nil,
    model: ModelHandle = HuggingFaceHandle("nixiesearch", "nixie-suggest-small-v1"),
    transform: Option[Transform] = None
) {
  lazy val index = IndexMapping(
    name = name,
    alias = alias,
    fields = Map(
      SUGGEST_FIELD -> TextFieldSchema(name = SUGGEST_FIELD, search = SemanticSearch(model = model), filter = true),
      "_id"         -> TextFieldSchema(name = "_id", filter = true)
    )
  )
}

object SuggestMapping {
  val SUGGEST_FIELD = "suggest"

  case class Transform(
      fields: List[String],
      language: Language = English,
      lowercase: Boolean = true,
      removeStopwords: Boolean = true,
      group: List[Int] = List(1, 2, 3)
  )

  object yaml {
    import Language.given

    given transformDecoder: Decoder[Transform] = Decoder.instance(c =>
      for {
        fields    <- c.downField("fields").as[List[String]]
        language  <- c.downField("language").as[Option[Language]].map(_.getOrElse(English))
        lowercase <- c.downField("lowercase").as[Option[Boolean]].map(_.getOrElse(true))
        stop <- c
          .downField("removeStopwords")
          .as[Option[Boolean]]
          .map(_.getOrElse(true))
        group <- c.downField("group").as[Option[List[Int]]].map(_.getOrElse(List(1, 2, 3)))
      } yield {
        Transform(fields, language, lowercase, stop, group)
      }
    )

    def suggesterMappingDecoder(name: String): Decoder[SuggestMapping] = Decoder.instance(c =>
      for {
        alias <- decodeAlias(c.downField("alias"))
        model <- c
          .downField("model")
          .as[Option[ModelHandle]]
          .map(_.getOrElse(HuggingFaceHandle("nixiesearch", "nixie-suggest-small-v1")))
        tf <- c.downField("transform").as[Option[Transform]]
      } yield {
        SuggestMapping(
          name = name,
          alias = alias,
          model = model,
          transform = tf
        )
      }
    )
  }
}
