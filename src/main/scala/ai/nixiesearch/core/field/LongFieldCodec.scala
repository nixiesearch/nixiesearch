package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{LongField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.LongStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Json}
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField

import scala.util.{Failure, Success, Try}

case class LongFieldCodec(spec: LongFieldSchema) extends FieldCodec[LongField] {
  import FieldCodec.*
  override def writeLucene(
      field: LongField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      buffer.parent.add(new org.apache.lucene.document.LongField(field.name, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.parent.add(new NumericDocValuesField(field.name + SORT_SUFFIX, field.value))
    }
    if (spec.facet) {
      buffer.parent.add(new SortedNumericDocValuesField(field.name, field.value))
    }
    if (spec.store) {
      buffer.parent.add(new StoredField(field.name, field.value))
    }
  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, Option[LongField]] =
    doc.fields.collectFirst { case f @ LongStoredField(name, _) if spec.name.matches(name) => f } match {
      case Some(value) => Right(Some(LongField(value.name, value.value)))
      case None        => Right(None)
    }

  override def encodeJson(field: LongField): Json = Json.fromLong(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[LongField]] = {
    decodeJsonImpl(name, reader.readLong).map(value => Some(LongField(name, value)))
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.LONG, reverse)
    sortField.setMissingValue(MissingValue.of(min = Long.MinValue, max = Long.MaxValue, reverse, missing))
    Right(sortField)
  }
}
