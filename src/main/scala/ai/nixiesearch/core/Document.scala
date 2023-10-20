package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

import java.util.UUID
import scala.annotation.tailrec

case class Document(fields: List[Field])

object Document {
  def apply(head: Field, tail: Field*) = new Document(head +: tail.toList)

  given documentEncoder: Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case FloatField(name, value)          => (name, Json.fromFloatOrNull(value))
        case IntField(name, value)            => (name, Json.fromInt(value))
        case TextField(name, value)           => (name, Json.fromString(value))
        case Field.TextListField(name, value) => (name, Json.fromValues(value.map(Json.fromString)))
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

  given documentDecoder: Decoder[Document] =
    Decoder
      .instance(c =>
        c.value.asObject match {
          case None => Left(DecodingFailure(s"document should be a JSON object: ${c.value}", c.history))
          case Some(obj) =>
            decodeObject(c, obj.toList).map(fields => Document(fields.reverse))
        }
      )
      .ensure(_.fields.nonEmpty, "document cannot contain zero fields")

  @tailrec
  def decodeObject(c: HCursor, next: List[(String, Json)], acc: List[Field] = Nil): Decoder.Result[List[Field]] =
    next match {
      case Nil => Right(acc)
      case ("_id", json) :: tail =>
        decodeId(c, json) match {
          case Left(error) => Left(error)
          case Right(id)   => decodeObject(c, tail, id +: acc)
        }
      case (name, json) :: tail =>
        decodeField(c, name, json) match {
          case Left(error)        => Left(error)
          case Right(None)        => decodeObject(c, tail, acc)
          case Right(Some(field)) => decodeObject(c, tail, field +: acc)
        }
    }

  def decodeId(c: HCursor, json: Json): Decoder.Result[Field] = json.fold[Either[DecodingFailure, Field]](
    jsonNull = Right(TextField("_id", UUID.randomUUID().toString)),
    jsonBoolean = bool => Left(DecodingFailure(s"_id field can be either a number or a string, got $bool", c.history)),
    jsonNumber = num =>
      num.toLong match {
        case Some(long) => Right(TextField("_id", num.toString))
        case None =>
          Left(
            DecodingFailure(
              s"_id field cannot be a real number, but string|long|uuid, got $num",
              c.history
            )
          )
      },
    jsonString = str => Right(TextField("_id", str)),
    jsonArray = arr => Left(DecodingFailure(s"_id field cannot be an array, got $arr", c.history)),
    jsonObject = obj => Left(DecodingFailure(s"_id field cannot be an object, got $obj", c.history))
  )

  def decodeField(c: HCursor, name: String, json: Json): Decoder.Result[Option[Field]] = json.fold(
    jsonNull = Right(None),
    jsonBoolean = _ => Left(DecodingFailure("cannot parse null field", c.history)),
    jsonNumber = n =>
      n.toInt match {
        case Some(int) => Right(Some(IntField(name, int)))
        case None      => Right(Some(FloatField(name, n.toFloat)))

      },
    jsonString = s => Right(Some(TextField(name, s))),
    jsonArray = _ => Left(DecodingFailure("cannot parse array field", c.history)),
    jsonObject = _ => Left(DecodingFailure("cannot parse object field", c.history))
  )
}
