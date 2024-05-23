package ai.nixiesearch.config

import ai.nixiesearch.config.URL.{HttpURL, LocalURL, S3URL}
import ai.nixiesearch.config.URLTest.URLWrap
import io.circe.{Codec, Decoder, Encoder, Json, JsonObject}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.generic.semiauto.*
import org.http4s.Uri
import io.circe.yaml.parser.parse
import java.nio.file.Paths

class URLTest extends AnyFlatSpec with Matchers {
  it should "decode relative local dirs" in {
    decodeURL("test") shouldBe a[Right[?, ?]]
  }
  it should "decode local urls with no schema" in {
    decodeURL("/foo/bar") shouldBe Right(LocalURL(Paths.get("/foo/bar")))
  }

  it should "decode local urls with 2 slash schema" in {
    decodeURL("file://foo/bar") shouldBe Right(LocalURL(Paths.get("/foo/bar")))
  }

  it should "decode local urls with 3 slash schema" in {
    decodeURL("file:///foo/bar") shouldBe Right(LocalURL(Paths.get("/foo/bar")))
  }

  it should "decode s3 urls" in {
    decodeURL("s3://bucket/name") shouldBe Right(S3URL("bucket", "name"))
  }

  it should "fail on broken s3 buckets" in {
    decodeURL("s3://a") shouldBe a[Left[?, ?]]
    decodeURL("s3://a/") shouldBe a[Left[?, ?]]
    decodeURL("s3://") shouldBe a[Left[?, ?]]
    decodeURL("s3://fuc!/aaa") shouldBe a[Left[?, ?]]
  }

  it should "decode valid http urls" in {
    decodeURL("http://google.com") shouldBe Right(HttpURL(Uri.unsafeFromString("http://google.com")))
  }

  it should "decode s3 urls in full format" in {
    val yml =
      """
        |s3:
        |  bucket: foo
        |  prefix: bar
        |  region: us-east-1""".stripMargin
    val decoded = parse(yml).flatMap(_.as[URL])
    decoded shouldBe Right(S3URL("foo", "bar", Some("us-east-1")))
  }

  def decodeURL(value: String): Either[io.circe.Error, URL] = {
    val json = Json.fromJsonObject(JsonObject.fromMap(Map("url" -> Json.fromString(value)))).noSpaces
    decode[URLWrap](json).map(_.url)
  }
}

object URLTest {
  case class URLWrap(url: URL)
  given urlCodec: Codec[URLWrap] = deriveCodec
}
