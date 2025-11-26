package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.GeopointFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.GeopointField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Failure, Try}

class GeopointFieldJsonTest extends AnyFlatSpec with Matchers with FieldJsonTest {
  it should "decode geopoint with lat/lon" in {
    val result = decode(GeopointFieldSchema(StringName("point")), """{"point": {"lat": 40.7128, "lon": -74.0060}}""")
    result shouldBe Some(GeopointField("point", 40.7128, -74.0060))
  }

  it should "decode geopoint with lon/lat order" in {
    val result = decode(GeopointFieldSchema(StringName("point")), """{"point": {"lon": -74.0060, "lat": 40.7128}}""")
    result shouldBe Some(GeopointField("point", 40.7128, -74.0060))
  }

  it should "fail on missing lat" in {
    val result = Try(decode(GeopointFieldSchema(StringName("point")), """{"point": {"lon": -74.0060}}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on missing lon" in {
    val result = Try(decode(GeopointFieldSchema(StringName("point")), """{"point": {"lat": 40.7128}}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on invalid field name" in {
    val result =
      Try(decode(GeopointFieldSchema(StringName("point")), """{"point": {"lat": 40.7128, "salat": -74.0060}}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on non-object values" in {
    val result = Try(decode(GeopointFieldSchema(StringName("point")), """{"point": "40.7128,-74.0060"}"""))
    result shouldBe a[Failure[?]]
  }

  it should "fail on null values" in {
    val result = Try(decode(GeopointFieldSchema(StringName("point")), """{"point": null}"""))
    result shouldBe a[Failure[?]]
  }
}
