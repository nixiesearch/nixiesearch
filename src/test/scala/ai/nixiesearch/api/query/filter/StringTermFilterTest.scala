package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class StringTermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      IdFieldSchema(StringName("_id")),
      TextFieldSchema(StringName("color"), filter = true),
      TextFieldSchema(StringName("color2"), filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(IdField("_id", "1"), TextField("color", "red"))),
    Document(List(IdField("_id", "2"), TextField("color", "white"), TextField("color2", "light white"))),
    Document(List(IdField("_id", "3"), TextField("color", "red")))
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
