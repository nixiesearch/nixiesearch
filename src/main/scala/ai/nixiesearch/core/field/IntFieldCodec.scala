package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{IntField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.IntStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Json}
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.queries.function.docvalues.IntDocValues
import org.apache.lucene.search.SortField

import scala.util.{Failure, Success, Try}

case class IntFieldCodec(spec: IntFieldSchema) extends FieldCodec[IntField] {
  import FieldCodec.*
  override def writeLucene(
      field: IntField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      buffer.parent.add(new org.apache.lucene.document.IntField(field.name, field.value, Store.NO))
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

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, Option[IntField]] =
    doc.fields.collectFirst { case f @ IntStoredField(name, _) if spec.name.matches(name) => f } match {
      case Some(value) => Right(Some(IntField(value.name, value.value)))
      case None        => Right(None)
    }

  override def encodeJson(field: IntField): Json = Json.fromInt(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[IntField]] = {
    decodeJsonImpl(name, reader.readInt).map(value => Some(IntField(name, value)))
  }

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.INT, reverse)
    sortField.setMissingValue(MissingValue.of(min = Int.MinValue, max = Int.MaxValue, reverse, missing))
    Right(sortField)
  }

}
