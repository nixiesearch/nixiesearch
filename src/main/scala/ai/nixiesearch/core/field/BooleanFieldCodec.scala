package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{BooleanFieldSchema, IntFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{BooleanField, IntField}
import ai.nixiesearch.core.codec.DocumentVisitor.StoredDocument
import FieldCodec.WireDecodingError
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
import org.apache.lucene.search.SortField

import scala.util.{Failure, Success, Try}

case class BooleanFieldCodec(spec: BooleanFieldSchema) extends FieldCodec[BooleanField] {
  val nested = IntFieldCodec(
    IntFieldSchema(
      name = spec.name,
      store = spec.store,
      sort = spec.sort,
      facet = spec.facet,
      filter = spec.filter,
      required = spec.required
    )
  )

  override def writeLucene(
      field: BooleanField,
      buffer: DocumentGroup
  ): Unit = {
    nested.writeLucene(IntField(field.name, toInt(field.value)), buffer)
  }

  override def readLucene(doc: StoredDocument): Either[WireDecodingError, Option[BooleanField]] = {
    nested.readLucene(doc).flatMap {
      case Some(IntField(name, value)) => fromInt(value).map(bool => Some(BooleanField(name, bool)))
      case None                        => Right(None)
    }
  }

  private def toInt(bool: Boolean): Int                               = if (bool) 1 else 0
  private def fromInt(value: Int): Either[WireDecodingError, Boolean] = value match {
    case 0     => Right(false)
    case 1     => Right(true)
    case other => Left(WireDecodingError(s"cannot decode int value of ${other} as boolean"))
  }

  override def encodeJson(field: BooleanField): Json = Json.fromBoolean(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[BooleanField]] = {
    decodeJsonImpl(name, reader.readBoolean).map(value => Some(BooleanField(name, value)))
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    nested.sort(field, reverse, missing)
}
