package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch}
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.suggest.SuggestCandidates
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.{SortedSetDocValuesField, StoredField, StringField, Document as LuceneDocument}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef

case class TextListField(name: String, value: List[String]) extends Field with TextLikeField

object TextListField extends FieldCodec[TextListField, TextListFieldSchema, List[String]] {
  import TextField.{MAX_FACET_SIZE, RAW_SUFFIX, MAX_FIELD_SEARCH_SIZE}

  def apply(name: String, value: String, values: String*) = new TextListField(name, value +: values.toList)

  override def writeLucene(
      field: TextListField,
      spec: TextListFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]]
  ): Unit = {
    field.value.foreach(item => {
      if (spec.store) {
        buffer.add(new StoredField(field.name, item))
      }
      if (spec.facet || spec.sort) {
        val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
        buffer.add(new SortedSetDocValuesField(field.name, new BytesRef(trimmed)))
      }
      if (spec.filter || spec.facet) {
        buffer.add(new StringField(field.name + RAW_SUFFIX, item, Store.NO))
      }
      spec.search match {
        case _: LexicalSearch | _: HybridSearch =>
          val trimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
          buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
        case _ =>
        // ignore
      }
      spec.suggest.foreach(schema => {
        field.value.foreach(value => {
          SuggestCandidates
            .fromString(schema, field.name, value)
            .foreach(candidate => {
              val s = SuggestField(field.name + TextField.SUGGEST_SUFFIX, candidate, 1)
              buffer.add(s)
            })
        })
      })

    })
  }

  override def readLucene(
      name: String,
      spec: TextListFieldSchema,
      value: List[String]
  ): Either[FieldCodec.WireDecodingError, TextListField] =
    Right(TextListField(name, value))

  override def encodeJson(field: TextListField): Json = Json.fromValues(field.value.map(Json.fromString))

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField =
    TextField.sort(field, reverse, missing)

}
