package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IdField, TextField}
import ai.nixiesearch.util.TestIndexMapping
import io.circe.Encoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*

class DocumentJsonTest extends AnyFlatSpec with Matchers {
  lazy val mapping                 = TestIndexMapping()
  given decoder: Encoder[Document] = Document.encoderFor(mapping)
  it should "encode all fields" in {
    val json =
      Document(FloatField("_score", 1.0), IdField("_id", "aaa"), TextField("title", "yes")).asJson.noSpacesSortKeys
    json shouldBe """{"_id":"aaa","_score":1.0,"title":"yes"}"""
  }
}
