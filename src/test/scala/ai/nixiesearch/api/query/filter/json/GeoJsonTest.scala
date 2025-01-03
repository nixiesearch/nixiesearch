package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{GeoBoundingBoxPredicate, GeoDistancePredicate, LatLon}
import ai.nixiesearch.util.Distance
import ai.nixiesearch.util.Distance.DistanceUnit.Kilometer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class GeoJsonTest extends AnyFlatSpec with Matchers {
  it should "decode geo_distance predicate" in {
    val json =
      """{
        | "include": {
        |   "geo_distance": {
        |     "field": "location",
        |     "lat": 1.0,
        |     "lon": 2.0,
        |     "distance": "1 km"
        |   }
        | }
        |}""".stripMargin
    val decoded = decode[Filters](json)
    decoded shouldBe Right(
      Filters(include =
        Some(
          GeoDistancePredicate(
            field = "location",
            point = LatLon(1.0, 2.0),
            distance = Distance(1.0, Kilometer)
          )
        )
      )
    )
  }
  it should "decode geo_box predicate" in {
    val json =
      """{
        | "include": {
        |   "geo_box": {
        |     "field": "location",
        |     "top_left": {"lat": 1.0, "lon": 2.0},
        |     "bottom_right": {"lat":3.0, "lon": 4.0}
        |   }
        | }
        |}""".stripMargin
    val decoded = decode[Filters](json)
    decoded shouldBe Right(
      Filters(include =
        Some(
          GeoBoundingBoxPredicate(
            field = "location",
            topLeft = LatLon(1.0, 2.0),
            bottomRight = LatLon(3.0, 4.0)
          )
        )
      )
    )
  }
}
