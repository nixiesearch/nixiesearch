package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IntField, TextField, TextListField}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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
          case Left(error)   => Left(error)
          case Right(fields) => decodeObject(c, tail, fields ++ acc)
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
              s"_id field cannot be a real number, but string|int|long|uuid, got $num",
              c.history
            )
          )
      },
    jsonString = str => Right(TextField("_id", str)),
    jsonArray = arr => Left(DecodingFailure(s"_id field cannot be an array, got $arr", c.history)),
    jsonObject = obj => Left(DecodingFailure(s"_id field cannot be an object, got $obj", c.history))
  )

  def decodeField(c: HCursor, name: String, json: Json): Decoder.Result[List[Field]] =
    json.fold[Decoder.Result[List[Field]]](
      jsonNull = Right(Nil),
      jsonBoolean = _ => Left(DecodingFailure(s"cannot parse null field $name=$json", c.history)),
      jsonNumber = n => Right(List(FloatField(name, n.toFloat))),
      jsonString = s => Right(List(TextField(name, s))),
      jsonArray = arr =>
        arr.headOption match {
          case None => Right(Nil)
          case Some(head) =>
            head.fold[Decoder.Result[List[Field]]](
              jsonNull = Right(Nil),
              jsonBoolean = _ => Left(DecodingFailure(s"arrays of booleans are not supported: $name=$head", c.history)),
              jsonNumber = _ => Left(DecodingFailure(s"arrays of numbers are not supported: $name=$head", c.history)),
              jsonString = _ => c.downField(name).as[List[String]].map(list => List(TextListField(name, list))),
              jsonArray = _ => Left(DecodingFailure(s"arrays of arrays are not supported: $name=$head", c.history)),
              jsonObject = obj => decodeArrayNestedObject(c, arr, name)
            )
        },
      jsonObject = obj => decodeNestedObject(c, obj, name)
    )

  def decodeNestedObject(c: HCursor, obj: JsonObject, prefix: String): Decoder.Result[List[Field]] = obj.toList match {
    case Nil => Right(Nil)
    case nested =>
      nested.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
        case (Left(error), _)              => Left(error)
        case (Right(acc), (subname, json)) => decodeField(c, s"$prefix.$subname", json)
      }
  }

  def decodeArrayNestedObject(c: HCursor, arr: Vector[Json], prefix: String): Decoder.Result[List[Field]] = {
    val strings = mutable.Map[String, List[String]]()
    val floats  = mutable.Map[String, List[Float]]()
    for {
      json           <- arr
      obj            <- json.asObject
      (field, value) <- obj.toList
    } {
      value.asString match {
        case None =>
          value.asNumber match {
            case None =>
            case Some(number) =>
              val flt = number.toFloat
              floats.updateWith(field) {
                case None       => Some(List(flt))
                case Some(prev) => Some(flt +: prev)
              }
          }
        case Some(string) =>
          strings.updateWith(field) {
            case None       => Some(List(string))
            case Some(prev) => Some(string +: prev)
          }
      }
    }
    val strFields = strings.map { case (name, values) => TextListField(s"$prefix.$name", values) }.toList
    Right(strFields)
  }

}
