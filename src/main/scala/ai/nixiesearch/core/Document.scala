package ai.nixiesearch.core

import ai.nixiesearch.api.filter.Predicate.FilterTerm.{DateTerm, DateTimeTerm}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.field.GeopointFieldCodec.Geopoint
import ai.nixiesearch.core.field.TextFieldCodec.TextEmbedding
import ai.nixiesearch.core.field.TextListFieldCodec.TextListEmbedding
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonNumber, JsonObject}
import cats.syntax.all.*
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
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
      val fields = doc.fields.flatMap {
        case FloatField("_score", score) => Some("_score" -> Json.fromDoubleOrNull(score))
        case field                       => encodeField(mapping, field).map(v => field.name -> v)
      }
      Json.fromJsonObject(JsonObject.fromIterable(fields))
    })

  private def encodeField[F <: Field, S <: FieldSchema[F]](mapping: IndexMapping, field: F)(using
      manifest: scala.reflect.ClassTag[S]
  ): Option[Json] =
    mapping.fieldSchema[S](StringName(field.name)).map(schema => schema.codec.encodeJson(field))

  def decoderFor(mapping: IndexMapping) = DocumentDecoder.codec(mapping)

}
