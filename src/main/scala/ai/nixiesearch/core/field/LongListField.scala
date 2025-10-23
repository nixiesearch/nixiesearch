package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{IntListFieldSchema, LongListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import io.circe.Json
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField, Document as LuceneDocument}
import org.apache.lucene.search.SortField

case class LongListField(name: String, value: List[Long]) extends Field with NumericField

object LongListField extends FieldCodec[LongListField, LongListFieldSchema, List[Long]] {
  override def writeLucene(
      field: LongListField,
      spec: LongListFieldSchema,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value => buffer.parent.add(new org.apache.lucene.document.LongField(field.name, value, Store.NO)))
    }
    if (spec.store) {
      field.value.foreach(value => buffer.parent.add(new StoredField(field.name, value)))
    }
    if (spec.facet) {
      field.value.foreach(value => buffer.parent.add(new SortedNumericDocValuesField(field.name, value)))
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
