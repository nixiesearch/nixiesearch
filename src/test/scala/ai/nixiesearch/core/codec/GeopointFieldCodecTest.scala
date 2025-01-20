package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.core.field.GeopointField
import org.apache.lucene.document.{Document, StoredField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class GeopointFieldCodecTest extends AnyFlatSpec with Matchers {
  it should "roundtrip stored geopoints" in {
    val doc    = new Document()
    val schema = GeopointFieldSchema(StringName("location"), store = true)
    GeopointField.writeLucene(GeopointField("location", 1, 2), schema, doc)
    val bytes   = doc.getField("location").asInstanceOf[StoredField]
    val decoded = GeopointField.readLucene("location", schema, bytes.binaryValue().bytes)
    decoded shouldBe Right(GeopointField("location", 1, 2))
  }
}
