package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{DateFieldSchema, IntFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import io.circe.{ACursor, Decoder, Encoder, Json}
import io.circe.Decoder.Result
import org.apache.lucene.document.Document
import org.apache.lucene.search.SortField

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.{Failure, Success, Try}

case class DateField(name: String, value: Int) extends Field

object DateField extends FieldCodec[DateField, DateFieldSchema, Int] {
  case class Date(value: Int)
  given dateEncoder: Encoder[Date] = Encoder.instance(days => Json.fromString(writeString(days.value)))
  given dateDecoder: Decoder[Date] = Decoder.decodeString.emapTry(str => parseString(str).map(Date.apply).toTry)

  override def readLucene(
      name: String,
      spec: DateFieldSchema,
      value: Int
  ): Either[FieldCodec.WireDecodingError, DateField] = {
    IntField.readLucene(name, spec.asInt, value).map(f => DateField(f.name, f.value))
  }

  override def writeLucene(
      field: DateField,
      spec: DateFieldSchema,
      buffer: Document,
      embeddings: Map[String, Array[Float]]
  ): Unit = {
    IntField.writeLucene(IntField(field.name, field.value), spec.asInt, buffer, embeddings)
  }

  override def encodeJson(field: DateField): Json = Json.fromString(writeString(field.value))

  def parseString(in: String): Either[Throwable, Int] = {
    Try(LocalDate.parse(in)) match {
      case Failure(exception) => Left(exception)
      case Success(date) =>
        val epoch = LocalDate.of(1970, 1, 1)
        Right(ChronoUnit.DAYS.between(epoch, date).toInt)
    }
  }

  def writeString(in: Int): String = {
    val epoch = LocalDate.of(1970, 1, 1)
    epoch.plusDays(in).toString
  }

  def applyUnsafe(name: String, str: String): DateField = {
    val epoch = LocalDate.of(1970, 1, 1)
    val date  = LocalDate.parse(str)
    val days  = ChronoUnit.DAYS.between(epoch, date).toInt
    new DateField(name, days)
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField =
    IntField.sort(field, reverse, missing)

}
