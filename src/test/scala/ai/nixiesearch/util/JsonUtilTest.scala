package ai.nixiesearch.util

import ai.nixiesearch.util.JsonUtilTest.DummyRequest
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonUtilTest extends AnyFlatSpec with Matchers {
  it should "pass matching keys" in {
    val result =
      JsonUtils.forbidExtraFields[DummyRequest](Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromString("foo")))
    result.isRight shouldBe true
  }
  it should "catch mismatches" in {
    val result =
      JsonUtils.forbidExtraFields[DummyRequest](
        Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromString("foo"), "c" -> Json.fromInt(2))
      )
    result.isRight shouldBe false
  }

}

object JsonUtilTest {
  case class DummyRequest(a: Int, b: String)
}
