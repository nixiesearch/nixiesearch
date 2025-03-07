package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.{DistanceSort, FieldValueSort}
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.First
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.ASC
import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.api.query.json.SortJsonTest.SortWrapper
import ai.nixiesearch.config.mapping.FieldName.StringName
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto.*
import io.circe.parser.*

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
    val result = parse("""{"by": {"name": {"geopoint": {"lat": 1.0, "lon": 2.0}}}}""")
    result shouldBe Right(DistanceSort(StringName("name"), geopoint = LatLon(1.0, 2.0)))
  }

  def parse(json: String): Either[io.circe.Error, SortPredicate] = {
    val result = decode[SortWrapper](json)
    result.map(_.by)
  }

}

object SortJsonTest {
  case class SortWrapper(by: SortPredicate)
  given wrapperDecoder: Decoder[SortWrapper] = deriveDecoder
}
