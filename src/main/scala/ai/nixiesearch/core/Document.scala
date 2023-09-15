package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

import java.util.UUID

case class Document(fields: List[Field])

object Document {

  implicit val documentEncoder: Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case FloatField(name, value)          => (name, Json.fromFloatOrNull(value))
        case IntField(name, value)            => (name, Json.fromInt(value))
        case TextField(name, value)           => (name, Json.fromString(value))
        case Field.TextListField(name, value) => (name, Json.fromValues(value.map(Json.fromString)))
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

  implicit val documentDecoder: Decoder[Document] =
    Decoder
      .instance(c =>
        c.value.asObject match {
          case None => Left(DecodingFailure(s"document should be a JSON object: ${c.value}", c.history))
          case Some(obj) =>
            val fields = obj.toMap
            val decoded = fields.toList.traverse {
              case ("_id", json) =>
                json.fold[Either[DecodingFailure, Field]](
                  jsonNull = Right(TextField("_id", UUID.randomUUID().toString)),
                  jsonBoolean = bool =>
                    Left(DecodingFailure(s"_id field can be either a number or a string, got $bool", c.history)),
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
              case (key, json) => decodeField(c, key, json)
            }
            decoded.map(fields => Document(fields))
        }
      )
      .ensure(_.fields.nonEmpty, "document cannot contain zero fields")

  def decodeField(c: HCursor, name: String, json: Json): Decoder.Result[Field] = json.fold(
    jsonNull = Left(DecodingFailure("cannot parse null field", c.history)),
    jsonBoolean = _ => Left(DecodingFailure("cannot parse null field", c.history)),
    jsonNumber = n =>
      n.toInt match {
        case Some(int) => Right(IntField(name, int))
        case None      => Left(DecodingFailure("cannot parse numeric field", c.history))
      },
    jsonString = s => Right(TextField(name, s)),
    jsonArray = _ => Left(DecodingFailure("cannot parse array field", c.history)),
    jsonObject = _ => Left(DecodingFailure("cannot parse object field", c.history))
  )
}
