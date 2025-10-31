package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.core.Field.GeopointField
import org.apache.lucene.document.{Document, StoredField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.field.GeopointFieldCodec
import ai.nixiesearch.core.search.DocumentGroup

class GeopointFieldCodecTest extends AnyFlatSpec with Matchers {
  it should "roundtrip stored geopoints" in {
    val doc    = DocumentGroup("id1")
    val schema = GeopointFieldSchema(StringName("location"), store = true)
    val codec  = GeopointFieldCodec(schema)
    codec.writeLucene(GeopointField("location", 1, 2), doc)
    val bytes = doc.parent.getField("location").asInstanceOf[StoredField]
    throw new NotImplementedError("todo")
//    val decoded = codec.readLucene("location", bytes.binaryValue().bytes)
//    decoded shouldBe Right(GeopointField("location", 1, 2))
  }
}
