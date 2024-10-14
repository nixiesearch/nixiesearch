package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.DoubleFieldSchema
import ai.nixiesearch.core.Field.DoubleField
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.util.NumericUtils

object DoubleFieldCodec extends FieldCodec[DoubleField, DoubleFieldSchema, Double] {
  override def write(
      field: DoubleField,
      spec: DoubleFieldSchema,
      buffer: Document,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.DoubleField(field.name, field.value, Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(field.value)))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }

  override def read(spec: DoubleFieldSchema, value: Double): Either[FieldCodec.WireDecodingError, DoubleField] =
    Right(DoubleField(spec.name, value))
}
