package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import org.apache.lucene.document.{Document, LatLonDocValuesField, LatLonPoint, StoredField}
import org.apache.lucene.util.BytesRef
import io.circe.generic.semiauto.*
import org.apache.lucene.search.SortField

import java.nio.ByteBuffer

case class GeopointField(name: String, lat: Double, lon: Double) extends Field

object GeopointField extends FieldCodec[GeopointField, GeopointFieldSchema, Array[Byte]] {
  override def writeLucene(
      field: GeopointField,
      spec: GeopointFieldSchema,
      buffer: Document
  ): Unit = {
    if (spec.store) {
      val buf = ByteBuffer.allocate(16)
      buf.putDouble(field.lat)
      buf.putDouble(field.lon)
      buffer.add(new StoredField(field.name, new BytesRef(buf.array())))
    }
    if (spec.filter) {
      buffer.add(new LatLonPoint(field.name, field.lat, field.lon))
    }
    if (spec.sort) {
      buffer.add(new LatLonDocValuesField(field.name + SORT_SUFFIX, field.lat, field.lon))
    }
  }

  override def readLucene(
      name: String,
      spec: GeopointFieldSchema,
      value: Array[Byte]
  ): Either[WireDecodingError, GeopointField] = {
    if (value.length != 16) {
      Left(WireDecodingError(s"geopoint stored payload should be 16 bytes, but it's ${value.length}"))
    } else {
      val buf = ByteBuffer.wrap(value)
      val lat = buf.getDouble()
      val lon = buf.getDouble()
      Right(GeopointField(name, lat, lon))
    }
  }

  override def encodeJson(field: GeopointField): Json =
    Json.obj("lat" -> Json.fromDoubleOrNull(field.lat), "lon" -> Json.fromDoubleOrNull(field.lon))

  def sort(
      field: FieldName,
      lat: Double,
      lon: Double
  ): SortField =
    LatLonDocValuesField.newDistanceSort(field.name + SORT_SUFFIX, lat, lon)

  case class Geopoint(lat: Double, lon: Double)
  object Geopoint {
    given geopointDecoder: Decoder[Geopoint] = deriveDecoder
    given geopointEncoder: Encoder[Geopoint] = deriveEncoder
  }
}
