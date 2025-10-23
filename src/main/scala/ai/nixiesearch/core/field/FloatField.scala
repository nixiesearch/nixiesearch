package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  KnnFloatVectorField,
  NumericDocValuesField,
  SortedDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils
case class FloatField(name: String, value: Float) extends Field with NumericField

object FloatField extends FieldCodec[FloatField, FloatFieldSchema, Float] {
  override def writeLucene(
      field: FloatField,
      spec: FloatFieldSchema,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      buffer.parent.add(new org.apache.lucene.document.FloatField(field.name, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.parent.add(
        new NumericDocValuesField(field.name + SORT_SUFFIX, NumericUtils.floatToSortableInt(field.value))
      )
    }
    if (spec.facet) {
      buffer.parent.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(field.value)))
    }
    if (spec.store) {
      buffer.parent.add(new StoredField(field.name, field.value))
    }
  }

  override def readLucene(
      name: String,
      spec: FloatFieldSchema,
      value: Float
  ): Either[FieldCodec.WireDecodingError, FloatField] =
    Right(FloatField(name, value))

  override def encodeJson(field: FloatField): Json = Json.fromFloatOrNull(field.value)

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.FLOAT, reverse)
    sortField.setMissingValue(MissingValue.of(min = Float.MinValue, max = Float.MaxValue, reverse, missing))
    sortField
  }

}
