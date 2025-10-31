package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.core.{Document, Field}
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import ai.nixiesearch.config.mapping.FieldName.StringName

object TestIndexMapping {
  given documentCodec: Codec[Document]                 = ???//Document.codecFor(apply())
  given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder[SearchResponse].mapJson(_.dropNullValues)
  given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder

  def apply(name: String, fields: List[? <: FieldSchema[? <: Field]]) = IndexMapping(
    name = IndexName.unsafe(name),
    fields = fields,
    store = LocalStoreConfig(MemoryLocation())
  )
  def apply() = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(lexical = Some(LexicalParams())),
        sort = true
      ),
      IntFieldSchema(name = StringName("price"), sort = true, facet = true, filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
}
