package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringTermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema("_id", filter = true),
      TextFieldSchema("color", filter = true),
      TextFieldSchema("color2", filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("color", "red"))),
    Document(List(TextField("_id", "2"), TextField("color", "white"), TextField("color2", "light white"))),
    Document(List(TextField("_id", "3"), TextField("color", "red")))
  )

  it should "select terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("color", "white")))))
      results shouldBe List("2")
    }
  }

  it should "select terms with space" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("color2", "light white")))))
      results shouldBe List("2")
    }
  }

}
