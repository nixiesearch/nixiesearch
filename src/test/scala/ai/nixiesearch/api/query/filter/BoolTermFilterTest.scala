package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{BooleanFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{BooleanField, TextField}
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation

class BoolTermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema("_id", filter = true),
      BooleanFieldSchema("color", filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), BooleanField("color", true))),
    Document(List(TextField("_id", "2"), BooleanField("color", false))),
    Document(List(TextField("_id", "3"), BooleanField("color", false)))
  )

  it should "select terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("color", true)))))
      results shouldBe List("1")
    }
  }

}
