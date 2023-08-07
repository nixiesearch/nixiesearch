package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IntField, TextField}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

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
            fields.get("id") match {
              case None =>
                Left(
                  DecodingFailure(
                    s"document is missing an 'id' field. Current fields: ${fields.keys.mkString("[", ",", "]")}",
                    c.history
                  )
                )
              case Some(json) =>
                json.asString match {
                  case None => Left(DecodingFailure(s"document 'id' field should have a string type", c.history))
                  case Some(id) =>
                    obj.toList.traverse(x => decodeField(c, x._1, x._2)).map(fields => Document(fields))
                }
            }
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
