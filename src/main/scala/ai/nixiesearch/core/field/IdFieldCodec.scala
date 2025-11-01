package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.IdField
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.StringStoredField
import ai.nixiesearch.core.field.FieldCodec.FILTER_SUFFIX
import ai.nixiesearch.core.field.TextFieldCodec.MAX_FACET_SIZE
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.{Decoder, DecodingFailure, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{SortedDocValuesField, StoredField, StringField}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.BytesRef

import scala.util.{Failure, Success, Try}

case class IdFieldCodec(spec: IdFieldSchema) extends FieldCodec[IdField] {
  override def decodeJson(name: String, reader: JsonReader): Either[JsonError, Option[IdField]] = {
    val tok = reader.nextToken()
    reader.rollbackToken()
    if (tok == '"') {
      decodeJsonImpl(name, () => reader.readString(null)).map(value => Some(IdField(name, value)))
    } else if ((tok >= '0') && (tok <= '9')) {
      decodeJsonImpl(name, reader.readInt).map(value => Some(IdField(name, value.toString)))
    } else {
      Left(JsonError(s"field $name: cannot parse id, got unknown token '$tok'"))
    }
  }

  override def encodeJson(field: IdField): Json = Json.fromString(field.value)

  override def sort(
      field: FieldName,
      reverse: Boolean,
      missing: SortPredicate.MissingValue
  ): Either[BackendError, SortField] = ???

  override def readLucene(
      doc: DocumentVisitor.StoredDocument
  ): Either[FieldCodec.WireDecodingError, Option[IdField]] = {
    doc.fields.collectFirst { case f @ StringStoredField(name, value) if spec.name.matches(name) => f } match {
      case Some(value) => Right(Some(IdField(value.name, value.value)))
      case None        => Right(None)
    }
  }

  override def writeLucene(field: IdField, buffer: DocumentGroup): Unit = {
    buffer.parent.add(new StoredField(field.name, field.value))
    val trimmed = if (field.value.length > MAX_FACET_SIZE) field.value.substring(0, MAX_FACET_SIZE) else field.value
    buffer.parent.add(new SortedDocValuesField(field.name, new BytesRef(trimmed)))
    buffer.parent.add(new StringField(field.name + FILTER_SUFFIX, field.value, Store.NO))
  }

  def sort(reverse: Boolean): Either[BackendError, SortField] = {
    val sortField = new SortField("_id", SortField.Type.STRING, reverse)
    Right(sortField)
  }

}
