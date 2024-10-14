package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.core.Field.IntField
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.Document as LuceneDocument

object IntFieldCodec extends FieldCodec[IntField, IntFieldSchema, Int] {
  override def write(
      field: IntField,
      spec: IntFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.IntField(field.name, field.value, Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, field.value))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }

  override def read(spec: IntFieldSchema, value: Int): Either[FieldCodec.WireDecodingError, IntField] = 
    Right(IntField(spec.name, value))
}
