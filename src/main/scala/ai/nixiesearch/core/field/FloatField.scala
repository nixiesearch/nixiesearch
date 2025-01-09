package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{KnnFloatVectorField, SortedDocValuesField, SortedNumericDocValuesField, StoredField, StringField, Document as LuceneDocument}
import org.apache.lucene.util.NumericUtils
case class FloatField(name: String, value: Float) extends Field with NumericField

object FloatField extends FieldCodec[FloatField, FloatFieldSchema, Float] {
  override def writeLucene(
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

  override def readLucene(name: String, spec: FloatFieldSchema, value: Float): Either[FieldCodec.WireDecodingError, FloatField] =
    Right(FloatField(name, value))

  override def encodeJson(field: FloatField): Json = Json.fromFloatOrNull(field.value)

  override def decodeJson(name: String, schema: FloatFieldSchema, json: Json): Result[Option[FloatField]] = {
    val parts = name.split('.').toList
    decodeRecursiveScalar[Float](parts, schema, json, _.as[Option[Float]], FloatField(name, _))
  }

}
