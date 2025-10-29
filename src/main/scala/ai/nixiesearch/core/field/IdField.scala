package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.field.TextField.{decodeEmbed, decodePlain}
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.{Decoder, DecodingFailure, Json}
import org.apache.lucene.search.SortField

import scala.util.{Failure, Success, Try}

case class IdField(name: String, value: String) extends Field {}

object IdField extends FieldCodec[IdField, IdFieldSchema, String] {
  override def decodeJson(spec: IdFieldSchema, name: String, reader: JsonReader): Either[JsonError, IdField] = {
    val tok = reader.nextToken()
    reader.rollbackToken()
    if (tok == '"') {
      Try(reader.readString(null)) match {
        case Failure(err) => Left(JsonError(s"field $name: cannot parse string", err))
        case Success(null) => Left(JsonError(s"field $name: got null"))
        case Success(value) => Right(IdField(name, value))
      }
    } else if ( (tok >= '0') && (tok <= '9')) {

      Try(reader.readInt()) match {
        case Failure(err) => Left(JsonError(s"field $name: cannot parse int", err))
        case Success(value) => Right(IdField(name, value.toString))
      }
    } else {
      Left(JsonError(s"field $name: cannot parse id, got unknown token '$tok'"))
    }
  }

  override def makeDecoder(spec: IdFieldSchema, fieldName: String): Decoder[Option[IdField]] =
    Decoder.instance(c =>
      c.as[String] match {
        case Left(err1) =>
          c.as[Int] match {
            case Left(err2) => Left(DecodingFailure(s"cannot decode $fieldName field: $err1, $err2", c.history))
            case Right(int) => Right(Some(IdField(fieldName, int.toString)))
          }
        case Right(str) => Right(Some(IdField(fieldName, str)))
      }
    )

  override def encodeJson(field: IdField): Json = Json.obj("_id" -> Json.fromString(field.value))

  override def readLucene(
      name: String,
      spec: IdFieldSchema,
      value: String
  ): Either[FieldCodec.WireDecodingError, IdField] = ???

  override def writeLucene(field: IdField, spec: IdFieldSchema, buffer: DocumentGroup): Unit = ???

  def sort(reverse: Boolean): SortField = {
    val sortField = new SortField("_id", SortField.Type.STRING, reverse)
    sortField
  }

}
