package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.GeopointField
import FieldCodec.WireDecodingError
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField
import ai.nixiesearch.core.field.GeopointFieldCodec.Geopoint
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import org.apache.lucene.document.{Document, LatLonDocValuesField, LatLonPoint, StoredField}
import org.apache.lucene.util.BytesRef
import io.circe.generic.semiauto.*
import org.apache.lucene.search.SortField

import java.nio.ByteBuffer

case class GeopointFieldCodec(spec: GeopointFieldSchema) extends FieldCodec[GeopointField] {
  import FieldCodec.*
  override def writeLucene(
      field: GeopointField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.store) {
      val buf = ByteBuffer.allocate(16)
      buf.putDouble(field.lat)
      buf.putDouble(field.lon)
      buffer.parent.add(new StoredField(field.name, new BytesRef(buf.array())))
    }
    if (spec.filter) {
      buffer.parent.add(new LatLonPoint(field.name, field.lat, field.lon))
    }
    if (spec.sort) {
      buffer.parent.add(new LatLonDocValuesField(field.name + SORT_SUFFIX, field.lat, field.lon))
    }
  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, Option[GeopointField]] =
    doc.fields
      .collectFirst {
        case f @ StoredLuceneField.BinaryStoredField(name, value) if spec.name.matches(name) => f
      } match {
      case Some(value) =>
        if (value.value.length != 16) {
          Left(WireDecodingError(s"geopoint stored payload should be 16 bytes, but it's ${value.value.length}"))
        } else {
          val buf = ByteBuffer.wrap(value.value)
          val lat = buf.getDouble()
          val lon = buf.getDouble()
          Right(Some(GeopointField(value.name, lat, lon)))
        }
      case None => Right(None)
    }

  override def encodeJson(field: GeopointField): Json =
    Json.obj("lat" -> Json.fromDoubleOrNull(field.lat), "lon" -> Json.fromDoubleOrNull(field.lon))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, GeopointField]   = ???
  override def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField] = Left(
    BackendError("???")
  )

}

object GeopointFieldCodec {
  import FieldCodec.*
  case class Geopoint(lat: Double, lon: Double)
  object Geopoint {
    given geopointDecoder: Decoder[Geopoint] = deriveDecoder
    given geopointEncoder: Encoder[Geopoint] = deriveEncoder
  }

  def sortBy(
      field: FieldName,
      lat: Double,
      lon: Double
  ): Either[BackendError, SortField] =
    Right(LatLonDocValuesField.newDistanceSort(field.name + SORT_SUFFIX, lat, lon))
}
