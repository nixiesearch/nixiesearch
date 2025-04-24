package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField

case class LongField(name: String, value: Long) extends Field with NumericField

object LongField extends FieldCodec[LongField, LongFieldSchema, Long] {
  override def writeLucene(
      field: LongField,
      spec: LongFieldSchema,
      buffer: Document
  ): Unit = {
    if (spec.filter) {
      buffer.add(new org.apache.lucene.document.LongField(field.name, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.add(new NumericDocValuesField(field.name + SORT_SUFFIX, field.value))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, field.value))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }

  override def readLucene(
      name: String,
      spec: LongFieldSchema,
      value: Long
  ): Either[FieldCodec.WireDecodingError, LongField] =
    Right(LongField(name, value))

  override def encodeJson(field: LongField): Json = Json.fromLong(field.value)

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.LONG, reverse)
    sortField.setMissingValue(MissingValue.of(min = Long.MinValue, max = Long.MaxValue, reverse, missing))
    sortField

  }
}
