package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{Document, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store

case class LongField(name: String, value: Long) extends Field with NumericField

object LongField extends FieldCodec[LongField, LongFieldSchema, Long] {
  override def writeLucene(
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

  override def readLucene(
      name: String,
      spec: LongFieldSchema,
      value: Long
  ): Either[FieldCodec.WireDecodingError, LongField] =
    Right(LongField(name, value))

  override def encodeJson(field: LongField): Json = Json.fromLong(field.value)

}
