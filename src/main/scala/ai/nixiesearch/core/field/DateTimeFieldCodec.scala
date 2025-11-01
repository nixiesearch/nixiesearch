package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{DateTimeFieldSchema, LongFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{DateTimeField, LongField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.field.DateTimeFieldCodec.DateTime
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Encoder, Json}
import org.apache.lucene.document.Document
import org.apache.lucene.search.SortField

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

case class DateTimeFieldCodec(spec: DateTimeFieldSchema) extends FieldCodec[DateTimeField] {
  val nested = LongFieldCodec(
    LongFieldSchema(
      name = spec.name,
      store = spec.store,
      sort = spec.sort,
      facet = spec.facet,
      filter = spec.filter,
      required = spec.required
    )
  )

  override def readLucene(
      doc: DocumentVisitor.StoredDocument
  ): Either[FieldCodec.WireDecodingError, Option[DateTimeField]] =
    nested.readLucene(doc).map {
      case Some(value) => Some(DateTimeField(value.name, value.value))
      case None        => None
    }

  override def writeLucene(
      field: DateTimeField,
      buffer: DocumentGroup
  ): Unit =
    nested.writeLucene(LongField(field.name, field.value), buffer)

  override def encodeJson(field: DateTimeField): Json = Json.fromString(DateTimeFieldCodec.writeString(field.value))

  override def decodeJson(
      name: String,
      reader: JsonReader
  ): Either[DocumentDecoder.JsonError, Option[DateTimeField]] = {
    decodeJsonImpl(name, () => reader.readString(null)).flatMap(str =>
      DateTimeFieldCodec.parseString(str) match {
        case Left(err)    => Left(JsonError(s"field $name: cannot parse datetime '$str'", err))
        case Right(value) => Right(Some(DateTimeField(name, value)))
      }
    )
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    nested.sort(field, reverse, missing)
}

object DateTimeFieldCodec {
  case class DateTime(millis: Long)
  given dateTimeEncoder: Encoder[DateTime] = Encoder.encodeString.contramap(field => writeString(field.millis))
  given dateTimeDecode: Decoder[DateTime]  =
    Decoder.decodeString.emapTry(string => DateTimeFieldCodec.parseString(string).map(DateTime.apply).toTry)

  def parseString(in: String): Either[Throwable, Long] = {
    Try(ZonedDateTime.parse(in, DateTimeFormatter.ISO_DATE_TIME)) match {
      case Success(zoned) =>
        val utc = zoned.withZoneSameInstant(ZoneId.of("UTC"))
        Right(utc.toInstant.toEpochMilli)
      case Failure(err) => Left(err)
    }
  }

  def writeString(millis: Long): String = {
    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
  }

}
