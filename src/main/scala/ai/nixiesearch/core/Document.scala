package ai.nixiesearch.core

import ai.nixiesearch.api.filter.Predicate.FilterTerm.{DateTerm, DateTimeTerm}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document.JsonScalar.{JBoolean, JNumber, JString, JStringArray}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.field.GeopointField.Geopoint
import ai.nixiesearch.core.field.TextField.TextEmbedding
import ai.nixiesearch.core.field.TextListField.TextListEmbedding
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonNumber, JsonObject}
import cats.syntax.all.*
import io.circe.Decoder.{Result, resultInstance}
import io.circe.generic.semiauto.*
import org.http4s.DecodeResult

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

case class Document(fields: List[Field])

object Document {

  def apply(head: Field, tail: Field*) = new Document(head +: tail.toList)

  def encoderFor(mapping: IndexMapping): Encoder[Document] =
    Encoder.instance(doc => {
      val fields = doc.fields.map {
        case f @ FloatField(name, value)       => (name, FloatField.encodeJson(f))
        case f @ FloatListField(name, value)   => (name, FloatListField.encodeJson(f))
        case f @ DoubleField(name, value)      => (name, DoubleField.encodeJson(f))
        case f @ DoubleListField(name, value)  => (name, DoubleListField.encodeJson(f))
        case f @ IntField(name, value)         => (name, IntField.encodeJson(f))
        case f @ IntListField(name, value)     => (name, IntListField.encodeJson(f))
        case f @ LongField(name, value)        => (name, LongField.encodeJson(f))
        case f @ LongListField(name, value)    => (name, LongListField.encodeJson(f))
        case f @ TextField(name, value, _)     => (name, TextField.encodeJson(f))
        case f @ BooleanField(name, value)     => (name, BooleanField.encodeJson(f))
        case f @ TextListField(name, value, _) => (name, TextListField.encodeJson(f))
        case f @ GeopointField(name, _, _)     => (name, GeopointField.encodeJson(f))
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

//  def decoderFor2(mapping: IndexMapping): Decoder[Document] = Decoder.instance(cursor => {
//    mapping.fields.values.toList
//      .traverse {
//        case s: IdFieldSchema         => required(IdField.decodeJson(s).tryDecode(cursor), s)
//        case s: TextFieldSchema       => required(TextField.decodeJson(s).tryDecode(cursor), s)
//        case s: TextListFieldSchema   => required(TextListField.decodeJson(s).tryDecode(cursor), s)
//        case s: BooleanFieldSchema    => required(BooleanField.decodeJson(s).tryDecode(cursor), s)
//        case s: DateFieldSchema       => required(DateField.decodeJson(s).tryDecode(cursor), s)
//        case s: DateTimeFieldSchema   => required(DateTimeField.decodeJson(s).tryDecode(cursor), s)
//        case s: DoubleFieldSchema     => required(DoubleField.decodeJson(s).tryDecode(cursor), s)
//        case s: DoubleListFieldSchema => required(DoubleListField.decodeJson(s).tryDecode(cursor), s)
//        case s: FloatFieldSchema      => required(FloatField.decodeJson(s).tryDecode(cursor), s)
//        case s: FloatListFieldSchema  => required(FloatListField.decodeJson(s).tryDecode(cursor), s)
//        case s: GeopointFieldSchema   => required(GeopointField.decodeJson(s).tryDecode(cursor), s)
//        case s: IntFieldSchema        => required(IntField.decodeJson(s).tryDecode(cursor), s)
//        case s: IntListFieldSchema    => required(IntListField.decodeJson(s).tryDecode(cursor), s)
//        case s: LongFieldSchema       => required(LongField.decodeJson(s).tryDecode(cursor), s)
//        case s: LongListFieldSchema   => required(LongListField.decodeJson(s).tryDecode(cursor), s)
//      }
//      .flatMap(_.flatten match {
//        case Nil => Left(DecodingFailure("cannot decode empty document", cursor.history))
//        case nel => Right(Document(nel))
//      })
//  })

  // def decoderFor4(mapping: IndexMapping): Decoder[Document] = Decoder.

  def decoderFor3(mapping: IndexMapping): Decoder[Document] = Decoder.instance(cursor => {
    cursor.value.asObject match {
      case None      => Left(DecodingFailure("doc should be an object", cursor.history))
      case Some(obj) =>
        obj.toList
          .traverse { case (name, json) =>
            mapping.fieldSchema(name) match {
              case Some(schema) =>
                schema match {
                  case s: IdFieldSchema         => required(IdField.makeDecoder(s, name).decodeJson(json), s)
                  case s: TextFieldSchema       => required(TextField.makeDecoder(s, name).decodeJson(json), s)
                  case s: TextListFieldSchema   => required(TextListField.makeDecoder(s, name).decodeJson(json), s)
                  case s: BooleanFieldSchema    => required(BooleanField.makeDecoder(s, name).decodeJson(json), s)
                  case s: DateFieldSchema       => required(DateField.makeDecoder(s, name).decodeJson(json), s)
                  case s: DateTimeFieldSchema   => required(DateTimeField.makeDecoder(s, name).decodeJson(json), s)
                  case s: DoubleFieldSchema     => required(DoubleField.makeDecoder(s, name).decodeJson(json), s)
                  case s: DoubleListFieldSchema => required(DoubleListField.makeDecoder(s, name).decodeJson(json), s)
                  case s: FloatFieldSchema      => required(FloatField.makeDecoder(s, name).decodeJson(json), s)
                  case s: FloatListFieldSchema  => required(FloatListField.makeDecoder(s, name).decodeJson(json), s)
                  case s: GeopointFieldSchema   => required(GeopointField.makeDecoder(s, name).decodeJson(json), s)
                  case s: IntFieldSchema        => required(IntField.makeDecoder(s, name).decodeJson(json), s)
                  case s: IntListFieldSchema    => required(IntListField.makeDecoder(s, name).decodeJson(json), s)
                  case s: LongFieldSchema       => required(LongField.makeDecoder(s, name).decodeJson(json), s)
                  case s: LongListFieldSchema   => required(LongListField.makeDecoder(s, name).decodeJson(json), s)
                }
              case None => Right(None)
            }
          }
          .flatMap(_.flatten match {
            case Nil => Left(DecodingFailure("cannot decode empty document", cursor.history))
            case nel => Right(Document(nel))
          })
    }
  })

  def required[F <: Field, S <: FieldSchema[F]](field: Result[Option[F]], spec: S): Result[Option[F]] = {
    (spec.required, field) match {
      case (_, Right(None)) if spec.name.name == "_id" => Right(None) // auto-gen later
      case (false, _)                                  => field
      case (true, Left(err))                           => Left(err)
      case (true, Right(None))        => Left(DecodingFailure(f"field ${spec.name} is required but missing", Nil))
      case (true, Right(Some(value))) => Right(Some(value))
    }
  }

  def decoderFor(mapping: IndexMapping)                     = decoderFor3(mapping)
  def decoderFor1(mapping: IndexMapping): Decoder[Document] = Decoder.instance(cursor => {
    cursor.value.foldWith(DocumentParser(Nil, mapping)) match {
      case Left(err)    => Left(err)
      case Right(Nil)   => Left(DecodingFailure(s"document cannot be empty: ${cursor.value}", cursor.history))
      case Right(other) =>
        val missingFields =
          mapping.requiredFields.filterNot(schemaField => other.exists(docField => schemaField.matches(docField.name)))
        missingFields match {
          case Nil =>
            Right(Document(other))
          case nel =>
            Left(
              DecodingFailure(
                s"fields '${nel.map(_.name)}' is defined as required, but doc has only '${other.map(_.name)}' fields",
                cursor.history
              )
            )
        }

    }
  })
  def codecFor(mapping: IndexMapping): Codec[Document] = Codec.from(decoderFor3(mapping), encoderFor(mapping))

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
        case (Left(err), _)              => Left(err)
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
        case Some(text: TextFieldSchema) =>
          value.toJson.as[TextEmbedding] match {
            case Left(err)    => Left(DecodingFailure(s"cannot decode text field $fieldName for $value", Nil))
            case Right(value) => Right(List(TextField(fieldName, value.text, Some(value.embedding))))
          }
        case Some(text: TextListFieldSchema) =>
          value.toJson.as[TextListEmbedding] match {
            case Left(err)    => Left(DecodingFailure(s"cannot decode text[] field $fieldName for $value", Nil))
            case Right(value) =>
              Right(List(TextListField(fieldName, value = value.text, embeddings = value.embedding)))
          }
        case Some(other) =>
          Left(DecodingFailure(s"field $fieldName cannot be parsed from json object '$value'", Nil))
        case None =>
          value.toList.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
            case (Left(err), _)              => Left(err)
            case (Right(list), (name, json)) =>
              json.foldWith(DocumentParser(field :+ name, mapping)) match {
                case Left(err)    => Left(err)
                case Right(value) => Right(list ++ value)
              }
          }
      }
    }

    def parseList[T, F <: Field](
        values: Vector[Json],
        unpack: Json => Decoder.Result[T],
        wrap: (String, List[T]) => F
    ): Decoder.Result[List[F]] =
      values.foldLeft[Decoder.Result[List[T]]](Right(Nil)) {
        case (Left(err), _)            => Left(err)
        case (Right(result), nextJson) =>
          unpack(nextJson) match {
            case Left(err)    => Left(err)
            case Right(value) => Right(result :+ value)
          }
      } match {
        case Left(err)     => Left(err)
        case Right(Nil)    => Right(Nil)
        case Right(values) => Right(List(wrap(fieldName, values)))
      }

    def parseNumberArrayItemMaybe[T](value: Json, decode: JsonNumber => Option[T], name: String): Decoder.Result[T] =
      value.asNumber match {
        case None =>
          Left(DecodingFailure(s"array field '$fieldName' can only contain $name, but got $json", Nil))
        case Some(number) =>
          decode(number) match {
            case None =>
              Left(DecodingFailure(s"array field '$fieldName' can only contain $name, but got $json", Nil))
            case Some(value) => Right(value)
          }
      }

    def parseNumberArrayItem[T](value: Json, decode: JsonNumber => T, name: String): Decoder.Result[T] =
      value.asNumber match {
        case None =>
          Left(DecodingFailure(s"array field '$fieldName' can only contain $name, but got $json", Nil))
        case Some(number) =>
          Right(decode(number))
      }

    def parseStringArrayItem(value: Json): Decoder.Result[String] = value.asString match {
      case None      => Left(DecodingFailure(s"field '$fieldName' can only contain strings, but got $json", Nil))
      case Some(str) => Right(str)
    }

    override def onArray(value: Vector[Json]): Decoder.Result[List[Field]] =
      mapping.fieldSchema(fieldName) match {
        case Some(_: DoubleListFieldSchema) =>
          parseList(value, parseNumberArrayItem(_, _.toDouble, "double"), DoubleListField.apply)
        case Some(_: FloatListFieldSchema) =>
          parseList(value, parseNumberArrayItem(_, _.toFloat, "float"), FloatListField.apply)
        case Some(_: LongListFieldSchema) =>
          parseList(value, parseNumberArrayItemMaybe(_, _.toLong, "long"), LongListField.apply)
        case Some(_: IntListFieldSchema) =>
          parseList(value, parseNumberArrayItemMaybe(_, _.toInt, "int"), IntListField.apply)
        case Some(_: TextListFieldSchema) =>
          parseList(value, parseStringArrayItem, TextListField.apply)
        case Some(_) => Left(DecodingFailure(s"unexpected array for field '$fieldName': $value", Nil))
        case None    =>
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
