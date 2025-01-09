package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.DoubleFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{Document, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.util.NumericUtils

case class DoubleField(name: String, value: Double) extends Field with NumericField

object DoubleField extends FieldCodec[DoubleField, DoubleFieldSchema, Double] {
  override def writeLucene(
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

  override def readLucene(
      name: String,
      spec: DoubleFieldSchema,
      value: Double
  ): Either[FieldCodec.WireDecodingError, DoubleField] =
    Right(DoubleField(name, value))

  override def encodeJson(field: DoubleField): Json = Json.fromDoubleOrNull(field.value)

}
