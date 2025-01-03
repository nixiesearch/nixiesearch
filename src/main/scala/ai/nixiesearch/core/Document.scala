package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.FieldJson.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import cats.implicits.*

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

case class Document(fields: List[Field])

object Document {

  def apply(head: Field, tail: Field*) = new Document(head +: tail.toList)

  def encoderFor(mapping: IndexMapping): Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case f @ FloatField(name, value)          => (name, FloatFieldJson.encode(f))
        case f @ DoubleField(name, value)         => (name, DoubleFieldJson.encode(f))
        case f @ IntField(name, value)            => (name, IntFieldJson.encode(f))
        case f @ LongField(name, value)           => (name, LongFieldJson.encode(f))
        case f @ TextField(name, value)           => (name, TextFieldJson.encode(f))
        case f @ BooleanField(name, value)        => (name, BooleanFieldJson.encode(f))
        case f @ Field.TextListField(name, value) => (name, TextListFieldJson.encode(f))
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

  def decoderFor(mapping: IndexMapping): Decoder[Document] = Decoder.instance(cursor => {
    val fields: List[Decoder.Result[Option[Field]]] = mapping.fields.values.toList.map {
      case f: TextFieldSchema     => TextFieldJson.decode(f, cursor)
      case f: TextListFieldSchema => TextListFieldJson.decode(f, cursor)
      case f: IntFieldSchema      => IntFieldJson.decode(f, cursor)
      case f: FloatFieldSchema    => FloatFieldJson.decode(f, cursor)
      case f: LongFieldSchema     => LongFieldJson.decode(f, cursor)
      case f: DoubleFieldSchema   => DoubleFieldJson.decode(f, cursor)
      case f: BooleanFieldSchema  => BooleanFieldJson.decode(f, cursor)
    }
    val unwrapped = fields.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
      case (Left(err), _)                   => Left(err)
      case (Right(list), Left(err))         => Left(err)
      case (Right(list), Right(Some(next))) => Right(list :+ next)
      case (Right(list), Right(None))       => Right(list)
    }
    unwrapped match {
      case Left(err)     => Left(err)
      case Right(Nil)    => Left(DecodingFailure(s"decoded empty document for json '${cursor.value}'", cursor.history))
      case Right(fields) => Right(Document(fields))
    }
  })
  def codecFor(mapping: IndexMapping): Codec[Document] = Codec.from(decoderFor(mapping), encoderFor(mapping))

//  @tailrec
//  def decodeObject(c: HCursor, next: List[(String, Json)], acc: List[Field] = Nil): Decoder.Result[List[Field]] =
//    next match {
//      case Nil => Right(acc)
//      case ("_id", json) :: tail =>
//        decodeId(c, json) match {
//          case Left(error) => Left(error)
//          case Right(id)   => decodeObject(c, tail, id +: acc)
//        }
//      case (name, json) :: tail =>
//        decodeField(c, name, json) match {
//          case Left(error)   => Left(error)
//          case Right(fields) => decodeObject(c, tail, fields ++ acc)
//        }
//    }
//
//  def decodeId(c: HCursor, json: Json): Decoder.Result[Field] = json.fold[Either[DecodingFailure, Field]](
//    jsonNull = Right(TextField("_id", UUID.randomUUID().toString)),
//    jsonBoolean = bool => Left(DecodingFailure(s"_id field can be either a number or a string, got $bool", c.history)),
//    jsonNumber = num =>
//      num.toLong match {
//        case Some(long) => Right(TextField("_id", num.toString))
//        case None =>
//          Left(
//            DecodingFailure(
//              s"_id field cannot be a real number, but string|int|long|uuid, got $num",
//              c.history
//            )
//          )
//      },
//    jsonString = str => Right(TextField("_id", str)),
//    jsonArray = arr => Left(DecodingFailure(s"_id field cannot be an array, got $arr", c.history)),
//    jsonObject = obj => Left(DecodingFailure(s"_id field cannot be an object, got $obj", c.history))
//  )
//
//  def decodeField(c: HCursor, name: String, json: Json): Decoder.Result[List[Field]] =
//    json.fold[Decoder.Result[List[Field]]](
//      jsonNull = Right(Nil),
//      jsonBoolean = b => Right(List(BooleanField(name, b))),
//      jsonNumber = n => Right(List(FloatField(name, n.toFloat))),
//      jsonString = s => Right(List(TextField(name, s))),
//      jsonArray = arr =>
//        arr.headOption match {
//          case None => Right(Nil)
//          case Some(head) =>
//            head.fold[Decoder.Result[List[Field]]](
//              jsonNull = Right(Nil),
//              jsonBoolean = _ => Left(DecodingFailure(s"arrays of booleans are not supported: $name=$head", c.history)),
//              jsonNumber = _ => Left(DecodingFailure(s"arrays of numbers are not supported: $name=$head", c.history)),
//              jsonString = _ => c.downField(name).as[List[String]].map(list => List(TextListField(name, list))),
//              jsonArray = _ => Left(DecodingFailure(s"arrays of arrays are not supported: $name=$head", c.history)),
//              jsonObject = obj => decodeArrayNestedObject(c, arr, name)
//            )
//        },
//      jsonObject = obj => decodeNestedObject(c, obj, name)
//    )
//
//  def decodeNestedObject(c: HCursor, obj: JsonObject, prefix: String): Decoder.Result[List[Field]] = obj.toList match {
//    case Nil => Right(Nil)
//    case nested =>
//      nested.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
//        case (Left(error), _)              => Left(error)
//        case (Right(acc), (subname, json)) => decodeField(c, s"$prefix.$subname", json)
//      }
//  }
//
//  def decodeArrayNestedObject(c: HCursor, arr: Vector[Json], prefix: String): Decoder.Result[List[Field]] = {
//    val strings = mutable.Map[String, List[String]]()
//    val floats  = mutable.Map[String, List[Float]]()
//    for {
//      json           <- arr
//      obj            <- json.asObject
//      (field, value) <- obj.toList
//    } {
//      value.asString match {
//        case None =>
//          value.asNumber match {
//            case None =>
//              value.as[List[String]] match {
//                case Left(_) =>
//                case Right(values) =>
//                  strings.updateWith(field) {
//                    case None       => Some(values)
//                    case Some(prev) => Some(values ++ prev)
//                  }
//              }
//            case Some(number) =>
//              val flt = number.toFloat
//              floats.updateWith(field) {
//                case None       => Some(List(flt))
//                case Some(prev) => Some(flt +: prev)
//              }
//          }
//        case Some(string) =>
//          strings.updateWith(field) {
//            case None       => Some(List(string))
//            case Some(prev) => Some(string +: prev)
//          }
//      }
//    }
//    val strFields = strings.map { case (name, values) => TextListField(s"$prefix.$name", values) }.toList
//    Right(strFields)
//  }

}
