package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*
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
    cursor.focus match {
      case None => Left(DecodingFailure(s"Document cannot be empty: '${cursor.value}'", cursor.history))
      case Some(json) =>
        json.asObject match {
          case None =>
            Left(DecodingFailure(s"Document should be a json object, but got ${cursor.value}", cursor.history))
          case Some(obj) =>
            val fields: List[Decoder.Result[Option[Field]]] = obj.toList.map { case (fieldName, json) =>
              mapping.fieldSchema(fieldName) match {
                case Some(f: TextFieldSchema)     => TextField.decodeJson(fieldName, f, json)
                case Some(f: TextListFieldSchema) => TextListField.decodeJson(fieldName, f, json)
                case Some(f: IntFieldSchema)      => IntField.decodeJson(fieldName, f, json)
                case Some(f: FloatFieldSchema)    => FloatField.decodeJson(fieldName, f, json)
                case Some(f: LongFieldSchema)     => LongField.decodeJson(fieldName, f, json)
                case Some(f: DoubleFieldSchema)   => DoubleField.decodeJson(fieldName, f, json)
                case Some(f: BooleanFieldSchema)  => BooleanField.decodeJson(fieldName, f, json)
                case Some(f: GeopointFieldSchema) => GeopointField.decodeJson(fieldName, f, json)
                case Some(f: DateFieldSchema)     => DateField.decodeJson(fieldName, f, json)
                case Some(f: DateTimeFieldSchema) => DateTimeField.decodeJson(fieldName, f, json)
                case None                         => Left(DecodingFailure("", cursor.history))
              }
            }
            val unwrapped = fields.foldLeft[Decoder.Result[List[Field]]](Right(Nil)) {
              case (Left(err), _)                   => Left(err)
              case (Right(list), Left(err))         => Left(err)
              case (Right(list), Right(Some(next))) => Right(list :+ next)
              case (Right(list), Right(None))       => Right(list)
            }
            unwrapped match {
              case Left(err) => Left(err)
              case Right(Nil) =>
                Left(DecodingFailure(s"decoded empty document for json '${cursor.value}'", cursor.history))
              case Right(fields) => Right(Document(fields))
            }
        }
    }
  })
  def codecFor(mapping: IndexMapping): Codec[Document] = Codec.from(decoderFor(mapping), encoderFor(mapping))

}
