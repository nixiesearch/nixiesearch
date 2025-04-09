package ai.nixiesearch.core

import ai.nixiesearch.api.filter.Predicate.FilterTerm.{DateTerm, DateTimeTerm}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document.JsonScalar.{JBoolean, JNumber, JString, JStringArray}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.field.GeopointField.Geopoint
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonNumber, JsonObject}
import cats.implicits.*
import io.circe.Decoder.{Result, resultInstance}

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

case class Document(fields: List[Field])

object Document {

  def apply(head: Field, tail: Field*) = new Document(head +: tail.toList)

  def encoderFor(mapping: IndexMapping): Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case f @ FloatField(name, value)    => (name, FloatField.encodeJson(f))
        case f @ DoubleField(name, value)   => (name, DoubleField.encodeJson(f))
        case f @ IntField(name, value)      => (name, IntField.encodeJson(f))
        case f @ LongField(name, value)     => (name, LongField.encodeJson(f))
        case f @ TextField(name, value)     => (name, TextField.encodeJson(f))
        case f @ BooleanField(name, value)  => (name, BooleanField.encodeJson(f))
        case f @ TextListField(name, value) => (name, TextListField.encodeJson(f))
        case f @ GeopointField(name, _, _)  => (name, GeopointField.encodeJson(f))
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

  def decoderFor(mapping: IndexMapping): Decoder[Document] = Decoder.instance(cursor => {
    cursor.value.foldWith(DocumentParser(Nil, mapping)) match {
      case Left(err)  => Left(err)
      case Right(Nil) => Left(DecodingFailure(s"document cannot be empty: ${cursor.value}", cursor.history))
      case Right(other) =>
        if (other.exists(_.name == "_id")) {
          Right(Document(other))
        } else {
          val id = TextField("_id", UUID.randomUUID().toString)
          Right(Document(other :+ id))
        }

    }
  })
  def codecFor(mapping: IndexMapping): Codec[Document] = Codec.from(decoderFor(mapping), encoderFor(mapping))

  sealed trait JsonScalar
  object JsonScalar {
    case class JBoolean(value: Boolean)          extends JsonScalar
    case class JNumber(value: Double)            extends JsonScalar
    case class JString(value: String)            extends JsonScalar
    case class JStringArray(value: List[String]) extends JsonScalar
  }

  case class ArrayParser(field: List[String], mapping: IndexMapping)
      extends Json.Folder[Decoder.Result[List[TextListField]]]
      with Logging {
    val fieldName = field.mkString(".")

    override def onNull: Decoder.Result[List[TextListField]] = Left(
      DecodingFailure(s"field $fieldName cannot be null", Nil)
    )

    override def onNumber(value: JsonNumber): Result[List[TextListField]] =
      Left(DecodingFailure(s"field $fieldName cannot be number: $value", Nil))

    override def onBoolean(value: Boolean): Result[List[TextListField]] =
      Left(DecodingFailure(s"field $fieldName cannot be bool: $value", Nil))

    override def onString(value: String): Result[List[TextListField]] = {
      mapping.fieldSchema(fieldName) match {
        case Some(_: TextListFieldSchema) => Right(List(TextListField(fieldName, List(value))))
        case _                            => Right(Nil)
      }

    }

    override def onArray(value: Vector[Json]): Result[List[TextListField]] = {
      value.toList
        .map(json => json.foldWith(ArrayParser(field, mapping)))
        .reduce {
          case (Left(err), _)       => Left(err)
          case (_, Left(err))       => Left(err)
          case (Right(a), Right(b)) => Right(a ++ b)
        }
        .map(_.groupMapReduce(_.name)(identity) { case (a, b) =>
          TextListField(a.name, a.value ++ b.value)
        }.values.toList)
    }

    override def onObject(value: JsonObject): Result[List[TextListField]] = {
      value.toList.foldLeft[Decoder.Result[List[TextListField]]](Right(Nil)) {
        case (Left(err), _) => Left(err)
        case (Right(list), (name, json)) =>
          json.foldWith(ArrayParser(field :+ name, mapping)) match {
            case Left(err)    => Left(err)
            case Right(value) => Right(list ++ value)
          }
      }
    }

  }

  case class DocumentParser(field: List[String], mapping: IndexMapping)
      extends Json.Folder[Decoder.Result[List[Field]]]
      with Logging {
    val fieldName = field.mkString(".")

    override def onNull: Decoder.Result[List[Field]] = Left(DecodingFailure(s"field $fieldName cannot be null", Nil))

    override def onBoolean(value: Boolean): Decoder.Result[List[Field]] =
      mapping.fieldSchema(fieldName) match {
        case Some(_: BooleanFieldSchema) => Right(List(BooleanField(fieldName, value)))
        case Some(_) => Left(DecodingFailure(s"field $fieldName expected to be boolean, but got $value", Nil))
        case None    => Right(Nil)
      }

    override def onString(value: String): Decoder.Result[List[Field]] = {
      mapping.fieldSchema(fieldName) match {
        case Some(_: TextFieldSchema) => Right(List(TextField(fieldName, value)))
        case Some(_: DateFieldSchema) =>
          value match {
            case DateTerm(days) => Right(List(DateField(fieldName, days)))
            case _              => Left(DecodingFailure(s"date field '$fieldName' has wrong format '$value'", Nil))
          }
        case Some(_: DateTimeFieldSchema) =>
          value match {
            case DateTimeTerm(millis) => Right(List(DateTimeField(fieldName, millis)))
            case _ => Left(DecodingFailure(s"datetime field '$fieldName' has wrong format '$value'", Nil))
          }
        case Some(other) => Left(DecodingFailure(s"cannot parse field '$fieldName' for string value $value", Nil))
        case None        => Right(Nil)
      }
    }

    override def onNumber(value: JsonNumber): Decoder.Result[List[Field]] =
      mapping.fieldSchema(fieldName) match {
        case Some(_: TextFieldSchema) if fieldName == "_id" =>
          value.toLong match {
            case Some(long) => Right(List(TextField("_id", long.toString)))
            case None       => Left(DecodingFailure(s"cannot parse numeric _id field for value '$value'", Nil))
          }
        case Some(_: IntFieldSchema)    => Right(List(IntField(fieldName, value.toDouble.toInt)))
        case Some(_: LongFieldSchema)   => Right(List(LongField(fieldName, value.toDouble.toLong)))
        case Some(_: FloatFieldSchema)  => Right(List(FloatField(fieldName, value.toFloat)))
        case Some(_: DoubleFieldSchema) => Right(List(DoubleField(fieldName, value.toDouble)))
        case Some(other) => Left(DecodingFailure(s"cannot parse field '$fieldName' for numeric value $value", Nil))
        case _           => Right(Nil)
      }

    override def onObject(value: JsonObject): Decoder.Result[List[Field]] = {
      mapping.fieldSchema(fieldName) match {
        case Some(_: GeopointFieldSchema) =>
          value.toJson.as[Geopoint] match {
            case Left(err)    => Left(DecodingFailure(s"cannot decode geopoint field $fieldName for $value", Nil))
            case Right(value) => Right(List(GeopointField(fieldName, value.lat, value.lon)))
          }
        case Some(other) =>
          Left(DecodingFailure(s"field $fieldName cannot be parsed from json object '$value'", Nil))
        case None =>
          value.toList.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
            case (Left(err), _) => Left(err)
            case (Right(list), (name, json)) =>
              json.foldWith(DocumentParser(field :+ name, mapping)) match {
                case Left(err)    => Left(err)
                case Right(value) => Right(list ++ value)
              }
          }
      }
    }

    override def onArray(value: Vector[Json]): Decoder.Result[List[Field]] =
      mapping.fieldSchema(fieldName) match {
        case Some(_: TextListFieldSchema) =>
          value.foldLeft[Decoder.Result[List[String]]](Right(Nil)) {
            case (Left(err), _) => Left(err)
            case (Right(strings), json) =>
              json.asString match {
                case None => Left(DecodingFailure(s"field '$fieldName' can only contain strings, but got $json", Nil))
                case Some(str) => Right(strings :+ str)
              }
          } match {
            case Left(err)     => Left(err)
            case Right(Nil)    => Right(Nil)
            case Right(values) => Right(List(TextListField(fieldName, values)))
          }
        case Some(_) => Left(DecodingFailure(s"unexpected array for field '$fieldName': $value", Nil))
        case None =>
          val result = value.toList
            .map(json => json.foldWith(ArrayParser(field, mapping)))
            .reduceLeftOption {
              case (Left(err), _)       => Left(err)
              case (_, Left(err))       => Left(err)
              case (Right(a), Right(b)) => Right(a ++ b)
            }
          result match {
            case None =>
              Right(Nil)
            case Some(nel) =>
              nel.map(_.groupMapReduce(_.name)(identity) { case (a, b) =>
                TextListField(a.name, a.value ++ b.value)
              }.values.toList)
          }

      }
  }

}
