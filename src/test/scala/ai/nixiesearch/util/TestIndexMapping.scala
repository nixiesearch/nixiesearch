package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch

object TestIndexMapping {
  def apply() = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(name = "id"),
      TextFieldSchema(name = "title", search = LexicalSearch(), sort = true),
      IntFieldSchema(name = "price", sort = true, facet = true, filter = true)
    )
  )
}
