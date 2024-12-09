package ai.nixiesearch.api.query

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.{GeoBoundingBoxPredicate, GeoDistancePredicate, LatLon}
import ai.nixiesearch.config.FieldSchema.{GeopointFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{GeopointField, TextField}
import ai.nixiesearch.util.Distance.DistanceUnit.Kilometer
import ai.nixiesearch.util.{Distance, SearchTest, TestInferenceConfig}
import cats.effect.IO
import org.scalatest.matchers.should.Matchers
import fs2.io.readInputStream
import io.circe.parser.*
import cats.effect.unsafe.implicits.global

import java.util.zip.GZIPInputStream

class GeoQueryTest extends SearchTest with Matchers {
  override val inference = TestInferenceConfig.empty()
  val mapping = IndexMapping(
    name = IndexName("cities"),
    fields = List(
      TextFieldSchema("_id", filter = true),
      TextFieldSchema("city", facet = true),
      TextFieldSchema("country", facet = true),
      GeopointFieldSchema("location", filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )

  lazy val docs =
    readInputStream(IO(new GZIPInputStream(getClass.getResourceAsStream("/datasets/cities/cities.json.gz"))), 1024)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .evalMap(line => IO.fromEither(decode[Document](line)))
      .compile
      .toList
      .unsafeRunSync()

  it should "select cities close to berlin" in withIndex { index =>
    {
      val response =
        index.searchRaw(
          MatchAllQuery(),
          filters = Some(
            Filters(include = Some(GeoDistancePredicate("location", LatLon(52.52, 13.4049), Distance(200, Kilometer))))
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
