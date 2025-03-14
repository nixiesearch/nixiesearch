package ai.nixiesearch.api.query.sort

import ai.nixiesearch.api.SearchRoute.SortPredicate.DistanceSort
import ai.nixiesearch.config.FieldSchema.{GeopointFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.util.{CitiesDataset, SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class DistanceSortTest extends SearchTest with Matchers {
  val name    = StringName("field")
  val mapping = CitiesDataset.mapping
  val docs    = CitiesDataset()

  it should "sort by distance, desc" in withIndex { index =>
    {
      val response = index.searchRaw(
        fields = List("_id", "city"),
        sort =
          List(DistanceSort(StringName("location"), lat = CitiesDataset.BERLIN.lat, lon = CitiesDataset.BERLIN.lon)),
        n = 4
      )
      response.hits.flatMap(_.fields.collect { case TextField("city", city) => city }) shouldBe List(
        "Berlin",
        "Potsdam",
        "Cottbus",
        "Magdeburg"
      )
    }
  }

}
