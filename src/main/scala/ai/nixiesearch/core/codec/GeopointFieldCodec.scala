package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.core.Field.GeopointField
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import org.apache.lucene.document.{Document, LatLonDocValuesField, StoredField}
import org.apache.lucene.util.BytesRef

import java.nio.ByteBuffer

object GeopointFieldCodec extends FieldCodec[GeopointField, GeopointFieldSchema, Array[Byte]] {
  override def write(
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
      buffer.add(new LatLonDocValuesField(field.name, field.lat, field.lon))
    }
  }

  override def read(spec: GeopointFieldSchema, value: Array[Byte]): Either[WireDecodingError, GeopointField] = {
    if (value.length != 16) {
      Left(WireDecodingError(s"geopoint stored payload should be 16 bytes, but it's ${value.length}"))
    } else {
      val buf = ByteBuffer.wrap(value)
      val lat = buf.getDouble()
      val lon = buf.getDouble()
      Right(GeopointField(spec.name, lat, lon))
    }
  }
}
