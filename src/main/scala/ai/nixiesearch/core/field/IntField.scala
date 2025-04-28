package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.queries.function.docvalues.IntDocValues
import org.apache.lucene.search.SortField

case class IntField(name: String, value: Int) extends Field with NumericField

object IntField extends FieldCodec[IntField, IntFieldSchema, Int] {
  override def writeLucene(
      field: IntField,
      spec: IntFieldSchema,
      buffer: LuceneDocument
  ): Unit = {
    if (spec.filter) {
      buffer.add(new org.apache.lucene.document.IntField(field.name, field.value, Store.NO))
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
      spec: IntFieldSchema,
      value: Int
  ): Either[FieldCodec.WireDecodingError, IntField] =
    Right(IntField(name, value))

  override def encodeJson(field: IntField): Json = Json.fromInt(field.value)

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): SortField = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.INT, reverse)
    sortField.setMissingValue(MissingValue.of(min = Int.MinValue, max = Int.MaxValue, reverse, missing))
    sortField
  }

}
