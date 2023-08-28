package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping.yaml.decodeAlias
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import io.circe.Decoder

object SuggesterMapping {

  object yaml {
    def suggesterMappingDecoder(name: String): Decoder[IndexMapping] = Decoder.instance(c =>
      for {
        alias <- decodeAlias(c.downField("alias"))
        model <- c
          .downField("model")
          .as[Option[ModelHandle]]
          .map(_.getOrElse(HuggingFaceHandle("nixiesearch", "nixie-suggest-small-v1")))
      } yield {
        IndexMapping(
          name = name,
          alias = alias,
          fields = Map(
            "suggest" -> TextFieldSchema(name = "suggest", search = SemanticSearch(model = model))
          )
        )
      }
    )
  }
}
