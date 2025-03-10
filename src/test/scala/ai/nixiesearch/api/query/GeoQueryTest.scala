package ai.nixiesearch.api.query

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{GeoBoundingBoxPredicate, GeoDistancePredicate, LatLon}
import ai.nixiesearch.config.FieldSchema.{GeopointFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.Distance.DistanceUnit.Kilometer
import ai.nixiesearch.util.{CitiesDataset, Distance, SearchTest, TestInferenceConfig}
import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import fs2.io.readInputStream
import io.circe.parser.*
import cats.effect.unsafe.implicits.global

import java.util.zip.GZIPInputStream
import ai.nixiesearch.config.mapping.FieldName.StringName

class GeoQueryTest extends SearchTest with Matchers {
  override val inference = TestInferenceConfig.empty()
  val mapping            = CitiesDataset.mapping

  lazy val docs = CitiesDataset()

  it should "select cities close to berlin" in withIndex { index =>
    {
      val response =
        index.searchRaw(
          MatchAllQuery(),
          filters = Some(
            Filters(include = Some(GeoDistancePredicate("location", CitiesDataset.BERLIN, Distance(200, Kilometer))))
          ),
          fields = List("city"),
          n = 4
        )
      response.hits.flatMap(_.fields.collect { case TextField("city", city) => city }) shouldBe List(
        "Varnsdorf",
        "Berlin",
        "Leipzig",
        "Dresden"
      )

    }
  }

  it should "select berlin from a grid" in withIndex { index =>
    {
      val response =
        index.searchRaw(
          MatchAllQuery(),
          filters = Some(
            Filters(include = Some(GeoBoundingBoxPredicate("location", LatLon(51.52, 12.4049), LatLon(53.52, 14.4049))))
          ),
          fields = List("city"),
          n = 4
        )
      response.hits.flatMap(_.fields.collect { case TextField("city", city) => city }) shouldBe List(
        "Berlin",
        "Potsdam",
        "Cottbus"
      )
    }
  }
}
