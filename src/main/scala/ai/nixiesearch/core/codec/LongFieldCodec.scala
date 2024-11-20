package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.core.Field.LongField
import org.apache.lucene.document.{Document, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store

object LongFieldCodec extends FieldCodec[LongField, LongFieldSchema, Long] {
  override def write(
      field: LongField,
      spec: LongFieldSchema,
      buffer: Document,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.LongField(field.name, field.value, Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, field.value))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }

  override def read(spec: LongFieldSchema, value: Long): Either[FieldCodec.WireDecodingError, LongField] = 
    Right(LongField(spec.name, value))
}
