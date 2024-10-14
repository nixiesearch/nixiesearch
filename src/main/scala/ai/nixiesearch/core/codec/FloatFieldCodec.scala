package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.core.Field.FloatField
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField, Document as LuceneDocument}
import org.apache.lucene.util.NumericUtils

object FloatFieldCodec extends FieldCodec[FloatField, FloatFieldSchema, Float] {
  override def write(
      field: FloatField,
      spec: FloatFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.FloatField(field.name, field.value, Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(field.value)))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }

  override def read(spec: FloatFieldSchema, value: Float): Either[FieldCodec.WireDecodingError, FloatField] = 
    Right(FloatField(spec.name, value))
}
