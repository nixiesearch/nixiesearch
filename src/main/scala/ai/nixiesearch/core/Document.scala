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

}
