package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{FloatField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.FloatStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  KnnFloatVectorField,
  NumericDocValuesField,
  SortedDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

import scala.util.Try

case class FloatFieldCodec(spec: FloatFieldSchema) extends FieldCodec[FloatField] {
  import FieldCodec.*
  override def writeLucene(
      field: FloatField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      buffer.parent.add(new org.apache.lucene.document.FloatField(field.name, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.parent.add(
        new NumericDocValuesField(field.name + SORT_SUFFIX, NumericUtils.floatToSortableInt(field.value))
      )
    }
    if (spec.facet) {
      buffer.parent.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(field.value)))
    }
    if (spec.store) {
      buffer.parent.add(new StoredField(field.name, field.value))
    }
  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, List[FloatField]] =
    Right(
      doc.fields
        .collect { case f @ FloatStoredField(name, value) if spec.name.matches(StringName(name)) => f }
        .map(field => FloatField(field.name, field.value))
    )

  override def encodeJson(field: FloatField): Json = Json.fromFloatOrNull(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[FloatField]] =
    decodeJsonImpl(name, reader.readFloat).map(value => Some(FloatField(name, value)))

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.FLOAT, reverse)
    sortField.setMissingValue(MissingValue.of(min = Float.MinValue, max = Float.MaxValue, reverse, missing))
    Right(sortField)
  }

}
