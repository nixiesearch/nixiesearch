package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.DateTimeFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, Encoder, Json}
import org.apache.lucene.document.Document

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

case class DateTimeField(name: String, value: Long) extends Field

object DateTimeField extends FieldCodec[DateTimeField, DateTimeFieldSchema, Long] {
  case class DateTime(millis: Long)
  given dateTimeEncoder: Encoder[DateTime] = Encoder.encodeString.contramap(field => writeString(field.millis))
  given dateTimeDecode: Decoder[DateTime] =
    Decoder.decodeString.emapTry(string => parseString(string).map(DateTime.apply).toTry)

  def applyUnsafe(name: String, value: String): DateTimeField = new DateTimeField(name, parseString(value).toOption.get)

  override def readLucene(name: String, spec: DateTimeFieldSchema, value: Long): Either[FieldCodec.WireDecodingError, DateTimeField] =
    LongField.readLucene(name, spec.asLong, value).map(long => DateTimeField(name, long.value))

  override def writeLucene(
      field: DateTimeField,
      spec: DateTimeFieldSchema,
      buffer: Document,
      embeddings: Map[String, Array[Float]]
  ): Unit =
    LongField.writeLucene(LongField(field.name, field.value), spec.asLong, buffer, embeddings)

  override def encodeJson(field: DateTimeField): Json = Json.fromString(writeString(field.value))

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
