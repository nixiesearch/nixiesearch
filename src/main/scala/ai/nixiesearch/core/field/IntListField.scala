package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.IntListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import io.circe.Json
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField

case class IntListField(name: String, value: List[Int]) extends Field with NumericField

object IntListField extends FieldCodec[IntListField, IntListFieldSchema, List[Int]] {
  override def writeLucene(
      field: IntListField,
      spec: IntListFieldSchema,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value =>
        buffer.parent.add(new org.apache.lucene.document.IntField(field.name, value, Store.NO))
      )
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
      spec: IntListFieldSchema,
      value: List[Int]
  ): Either[FieldCodec.WireDecodingError, IntListField] =
    Right(IntListField(name, value))

  override def encodeJson(field: IntListField): Json = Json.fromValues(field.value.map(Json.fromInt))

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): SortField = ???

}
