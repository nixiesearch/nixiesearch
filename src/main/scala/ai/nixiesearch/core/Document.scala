package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field.{BooleanField, DoubleField, FloatField, IntField, LongField, TextField, TextListField}
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

case class Document(fields: List[Field]) {
  def cast(mapping: IndexMapping): Document = {
    Document(fields.map(castField(_, mapping)))
  }

  private def castField(field: Field, mapping: IndexMapping): Field = {
    mapping.fields.get(field.name) match {
      case None => field
      case Some(schema) =>
        (field, schema) match {
          case (f: IntField, s: IntFieldSchema)       => f
          case (f: IntField, s: LongFieldSchema)      => LongField(f.name, f.value)
          case (f: IntField, s: DoubleFieldSchema)    => DoubleField(f.name, f.value.toDouble)
          case (f: IntField, s: FloatFieldSchema)     => FloatField(f.name, f.value.toFloat)
          case (f: LongField, s: IntFieldSchema)      => IntField(f.name, f.value.toInt)
          case (f: LongField, s: LongFieldSchema)     => f
          case (f: LongField, s: DoubleFieldSchema)   => DoubleField(f.name, f.value.toDouble)
          case (f: LongField, s: FloatFieldSchema)    => FloatField(f.name, f.value.toFloat)
          case (f: FloatField, s: IntFieldSchema)     => IntField(f.name, f.value.toInt)
          case (f: FloatField, s: LongFieldSchema)    => LongField(f.name, f.value.toLong)
          case (f: FloatField, s: DoubleFieldSchema)  => DoubleField(f.name, f.value.toDouble)
          case (f: FloatField, s: FloatFieldSchema)   => f
          case (f: DoubleField, s: IntFieldSchema)    => IntField(f.name, f.value.toInt)
          case (f: DoubleField, s: LongFieldSchema)   => LongField(f.name, f.value.toLong)
          case (f: DoubleField, s: DoubleFieldSchema) => f
          case (f: DoubleField, s: FloatFieldSchema)  => FloatField(f.name, f.value.toFloat)
          case _                                      => field
        }
    }
  }
}

object Document {

  def apply(head: Field, tail: Field*) = new Document(head +: tail.toList)

  given documentEncoder: Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case FloatField(name, value)          => (name, Json.fromFloatOrNull(value))
        case DoubleField(name, value)         => (name, Json.fromDoubleOrNull(value))
        case IntField(name, value)            => (name, Json.fromInt(value))
        case LongField(name, value)           => (name, Json.fromLong(value))
        case TextField(name, value)           => (name, Json.fromString(value))
        case BooleanField(name, value)        => (name, Json.fromBoolean(value))
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
      jsonBoolean = b => Right(List(BooleanField(name, b))),
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
              value.as[List[String]] match {
                case Left(_) =>
                case Right(values) =>
                  strings.updateWith(field) {
                    case None       => Some(values)
                    case Some(prev) => Some(values ++ prev)
                  }
              }
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
