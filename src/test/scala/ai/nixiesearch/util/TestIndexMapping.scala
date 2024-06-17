package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation

object TestIndexMapping {
  def apply() = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = LexicalSearch(), sort = true),
      IntFieldSchema(name = "price", sort = true, facet = true, filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
}
