package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.DoubleFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{DoubleField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.DoubleStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Json}
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

import scala.util.{Failure, Success, Try}

case class DoubleFieldCodec(spec: DoubleFieldSchema) extends FieldCodec[DoubleField] {
  import FieldCodec.*

  override def writeLucene(
      field: DoubleField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      buffer.parent.add(new org.apache.lucene.document.DoubleField(field.name, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.parent.add(
        new NumericDocValuesField(field.name + SORT_SUFFIX, NumericUtils.doubleToSortableLong(field.value))
      )
    }
    if (spec.facet) {
      buffer.parent.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(field.value)))
    }
    if (spec.store) {
      buffer.parent.add(new StoredField(field.name, field.value))
    }
  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, List[DoubleField]] = {
    Right(
      doc.fields
        .collect { case f @ DoubleStoredField(name, value) if spec.name.matches(StringName(name)) => f }
        .map(doubleField => DoubleField(doubleField.name, doubleField.value))
    )
  }

  override def encodeJson(field: DoubleField): Json = Json.fromDoubleOrNull(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[DoubleField]] = {
    decodeJsonImpl(name, reader.readDouble).map(value => Some(DoubleField(name, value)))
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.DOUBLE, reverse)
    sortField.setMissingValue(MissingValue.of(min = Double.MinValue, max = Double.MaxValue, reverse, missing))
    Right(sortField)
  }

}
