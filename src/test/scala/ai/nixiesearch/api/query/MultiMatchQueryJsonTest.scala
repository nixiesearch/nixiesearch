package ai.nixiesearch.api.query

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._

class MultiMatchQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "encode multi-match" in {
    val json = MultiMatchQuery("foo", List("a", "b")).asJson.noSpaces
    json shouldBe """{"query":"foo","fields":["a","b"]}"""
  }

  it should "decode multi-match" in {
    val decoded = decode[MultiMatchQuery]("""{"query":"foo","fields":["a","b"]}""")
    decoded shouldBe Right(MultiMatchQuery("foo", List("a", "b")))
  }
}
