package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.core.Field.GeopointField
import org.apache.lucene.document.{Document, StoredField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GeopointFieldCodecTest extends AnyFlatSpec with Matchers {
  it should "roundtrip stored geopoints" in {
    val doc    = new Document()
    val schema = GeopointFieldSchema("location", store = true)
    GeopointFieldCodec.write(GeopointField("location", 1, 2), schema, doc)
    val bytes   = doc.getField("location").asInstanceOf[StoredField]
    val decoded = GeopointFieldCodec.read(schema, bytes.binaryValue().bytes)
    decoded shouldBe Right(GeopointField("location", 1, 2))
  }
}
