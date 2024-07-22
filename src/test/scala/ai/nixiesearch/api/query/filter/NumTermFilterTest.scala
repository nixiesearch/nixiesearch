package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{BooleanFieldSchema, IntFieldSchema, LongFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{BooleanField, IntField, LongField, TextField}
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation

class NumTermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema("_id", filter = true),
      IntFieldSchema("int", filter = true),
      LongFieldSchema("long", filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), IntField("int", 1), LongField("long", 1))),
    Document(List(TextField("_id", "2"), IntField("int", 2), LongField("long", 2))),
    Document(List(TextField("_id", "3"), IntField("int", 3), LongField("long", 3)))
  )

  it should "select by int terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("int", 2)))))
      results shouldBe List("2")
    }
  }

  it should "select by long terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("long", 2)))))
      results shouldBe List("2")
    }
  }
}
