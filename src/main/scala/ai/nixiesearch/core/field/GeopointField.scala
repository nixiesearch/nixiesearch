package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import io.circe.Decoder.Result
import io.circe.{ACursor, DecodingFailure, Json}
import org.apache.lucene.document.{Document, LatLonPoint, StoredField}
import org.apache.lucene.util.BytesRef

import java.nio.ByteBuffer

case class GeopointField(name: String, lat: Double, lon: Double) extends Field

object GeopointField extends FieldCodec[GeopointField, GeopointFieldSchema, Array[Byte]] {
  override def writeLucene(
      field: GeopointField,
      spec: GeopointFieldSchema,
      buffer: Document,
      embeddings: Map[String, Array[Float]] = Map.empty
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
  }

  override def readLucene(name: String, spec: GeopointFieldSchema, value: Array[Byte]): Either[WireDecodingError, GeopointField] = {
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

  override def decodeJson(name: String,schema: GeopointFieldSchema, json: Json): Result[Option[GeopointField]] = ??? 
//    for {
//    latOption <- cursor.downField(name).downField("lat").as[Option[Double]]
//    lonOption <- cursor.downField(name).downField("lon").as[Option[Double]]
//    field <- (latOption, lonOption) match {
//      case (Some(lat), Some(lon)) => Right(Some(GeopointField(name, lat, lon)))
//      case (None, None)           => Right(None)
//      case (errLat, errLon) =>
//        Left(DecodingFailure(s"cannot decode geopoint field '$name' from ${cursor.focus}", cursor.history))
//    }
//  } yield {
//    field
//  }
}
