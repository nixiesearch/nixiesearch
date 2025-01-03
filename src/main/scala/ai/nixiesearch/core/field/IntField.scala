package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField, Document as LuceneDocument}
import org.apache.lucene.document.Field.Store

case class IntField(name: String, value: Int) extends Field with NumericField

object IntField extends FieldCodec[IntField, IntFieldSchema, Int] {
  override def writeLucene(
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

  override def readLucene(spec: IntFieldSchema, value: Int): Either[FieldCodec.WireDecodingError, IntField] =
    Right(IntField(spec.name, value))

  override def encodeJson(field: IntField): Json = Json.fromInt(field.value)

  override def decodeJson(schema: IntFieldSchema, cursor: ACursor): Result[Option[IntField]] = {
    val parts = schema.name.split('.').toList
    decodeRecursiveScalar[Int](parts, schema, cursor, _.as[Option[Int]], IntField(schema.name, _))
  }

}
