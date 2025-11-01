package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{DateFieldSchema, IntFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{DateField, IntField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.{ACursor, Decoder, Encoder, Json}
import io.circe.Decoder.Result
import org.apache.lucene.document.Document
import org.apache.lucene.search.SortField

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.{Failure, Success, Try}
import cats.syntax.all.*

case class DateFieldCodec(spec: DateFieldSchema) extends FieldCodec[DateField] {
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
  case class Date(value: Int)
  given dateEncoder: Encoder[Date] = Encoder.instance(days => Json.fromString(DateFieldCodec.writeString(days.value)))
  given dateDecoder: Decoder[Date] =
    Decoder.decodeString.emapTry(str => DateFieldCodec.parseString(str).map(Date.apply).toTry)

  override def readLucene(
      doc: DocumentVisitor.StoredDocument
  ): Either[FieldCodec.WireDecodingError, List[DateField]] = {
    nested.readLucene(doc).map(_.map(int => DateField(int.name, int.value)))
  }

  override def writeLucene(
      field: DateField,
      buffer: DocumentGroup
  ): Unit = {
    nested.writeLucene(IntField(field.name, field.value), buffer)
  }

  override def encodeJson(field: DateField): Json = Json.fromString(DateFieldCodec.writeString(field.value))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[DateField]] = {
    decodeJsonImpl(name, () => reader.readString(null)).flatMap(str =>
      DateFieldCodec.parseString(str) match {
        case Left(err)    => Left(JsonError(s"field $name: cannot parse date '$str'", err))
        case Right(value) => Right(Some(DateField(name, value)))
      }
    )
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    nested.sort(field, reverse, missing)

}

object DateFieldCodec {
  def parseString(in: String): Either[Throwable, Int] = {
    Try(LocalDate.parse(in)) match {
      case Failure(exception) => Left(exception)
      case Success(date)      =>
        val epoch = LocalDate.of(1970, 1, 1)
        Right(ChronoUnit.DAYS.between(epoch, date).toInt)
    }
  }

  def writeString(in: Int): String = {
    val epoch = LocalDate.of(1970, 1, 1)
    epoch.plusDays(in).toString
  }

}
