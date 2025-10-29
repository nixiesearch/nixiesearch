package ai.nixiesearch.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DocumentRawDecoderTest extends AnyFlatSpec with Matchers {

  it should "parse empty string" in {
    DocumentDecoder.decodeString("""""""", 1) shouldBe Right("")
  }

  it should "parse string with single char" in {
    DocumentDecoder.decodeString(""""yolo"""", 1) shouldBe Right("yolo")
  }

  it should "parse escaped quote" in {
    DocumentDecoder.decodeString(""""yo\"lo"""", 1) shouldBe Right("""yo"lo""")
  }

}
