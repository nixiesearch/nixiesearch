package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SortPredicate}
import ai.nixiesearch.api.SearchRoute.SortPredicate.{DistanceSort, FieldValueSort}
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.First
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.ASC
import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.api.query.json.SortJsonTest.SortWrapper
import ai.nixiesearch.config.mapping.FieldName.StringName
import io.circe.{Codec, Decoder}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*

class SortJsonTest extends AnyFlatSpec with Matchers {
  it should "decode field sort as string" in {
    val result = parse("""{"by": "name"}""")
    result shouldBe Right(FieldValueSort(StringName("name")))
  }

  it should "decode field as obj with all default fields" in {
    val result = parse("""{"by": {"name": {}}}""")
    result shouldBe Right(FieldValueSort(StringName("name")))
  }

  it should "decode field as obj with all non-def fields" in {
    val result = parse("""{"by": {"name": {"order": "asc", "missing":"first"}}}""")
    result shouldBe Right(FieldValueSort(StringName("name"), order = ASC, missing = First))
  }

  it should "decode geo sort" in {
    val result = parse("""{"by": {"name": {"lat": 1.0, "lon": 2.0}}}""")
    result shouldBe Right(DistanceSort(StringName("name"), lat = 1.0, lon = 2.0))
  }

  it should "fail on partial geo sort" in {
    val result = parse("""{"by": {"name": {"lat": 1.0}}}""")
    result shouldBe a[Left[?, ?]]
  }

  it should "fail on ambiguous sort" in {
    val result = parse("""{"by": {"name": {"lat": 1.0, "lon": 2.0, "order": "asc"}}}""")
    result shouldBe a[Left[?, ?]]
  }

  it should "round-trip field sort" in {
    roundtrip(FieldValueSort(StringName("name"), order = ASC, missing = First))
  }

  it should "round-trip geo sort" in {
    roundtrip(DistanceSort(StringName("name"), lat = 1.0, lon = 2.0))
  }

  it should "decode full search request with sort clause" in {
    val json   = """{"query": {"match_all": {}}, "sort": ["field"]}"""
    val result = decode[SearchRequest](json)
    result shouldBe Right(SearchRequest(query = MatchAllQuery(), sort = List(FieldValueSort(StringName("field")))))
  }

  def parse(json: String): Either[io.circe.Error, SortPredicate] = {
    val result = decode[SortWrapper](json)
    result.map(_.by)
  }

  def roundtrip(pred: SortPredicate) = {
    val parsed = parse(SortWrapper(pred).asJson.noSpaces)
    parsed shouldBe Right(pred)
  }

}

object SortJsonTest {
  case class SortWrapper(by: SortPredicate)
  given wrapperCodec: Codec[SortWrapper] = deriveCodec
}
