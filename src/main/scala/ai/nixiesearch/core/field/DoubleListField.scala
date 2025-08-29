package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{DoubleFieldSchema, DoubleListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Json
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

case class DoubleListField(name: String, value: List[Double]) extends Field with NumericField

object DoubleListField extends FieldCodec[DoubleListField, DoubleListFieldSchema, List[Double]] {
  override def writeLucene(
      field: DoubleListField,
      spec: DoubleListFieldSchema,
      buffer: Document
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value => buffer.add(new org.apache.lucene.document.DoubleField(field.name, value, Store.NO)))

    }
    if (spec.store) {
      field.value.foreach(value => buffer.add(new StoredField(field.name, value)))
    }
    if (spec.facet) {
      field.value.foreach(value =>
        buffer.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(value)))
      )
    }

  }

  override def readLucene(
      name: String,
      spec: DoubleListFieldSchema,
      value: List[Double]
  ): Either[FieldCodec.WireDecodingError, DoubleListField] =
    Right(DoubleListField(name, value))

  override def encodeJson(field: DoubleListField): Json = Json.fromValues(field.value.map(Json.fromDoubleOrNull))

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField = ???

}
