package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{IntListFieldSchema, LongListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Json
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{StoredField, Document as LuceneDocument}
import org.apache.lucene.search.SortField

case class LongListField(name: String, value: List[Long]) extends Field with NumericField

object LongListField extends FieldCodec[LongListField, LongListFieldSchema, List[Long]] {
  override def writeLucene(
      field: LongListField,
      spec: LongListFieldSchema,
      buffer: LuceneDocument
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value => buffer.add(new org.apache.lucene.document.LongField(field.name, value, Store.NO)))
    }
    if (spec.store) {
      field.value.foreach(value => buffer.add(new StoredField(field.name, value)))
    }
  }

  override def readLucene(
      name: String,
      spec: LongListFieldSchema,
      value: List[Long]
  ): Either[FieldCodec.WireDecodingError, LongListField] =
    Right(LongListField(name, value))

  override def encodeJson(field: LongListField): Json = Json.fromValues(field.value.map(Json.fromLong))

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): SortField = ???

}
