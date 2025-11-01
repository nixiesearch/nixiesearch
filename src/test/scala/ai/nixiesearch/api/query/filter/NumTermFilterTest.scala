package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{
  IdFieldSchema,
  IntFieldSchema,
  IntListFieldSchema,
  LongFieldSchema,
  LongListFieldSchema,
  TextFieldSchema
}
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.StringName

class NumTermFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      IdFieldSchema(StringName("_id")),
      IntFieldSchema(StringName("int"), filter = true),
      IntListFieldSchema(StringName("intlist"), filter = true),
      LongFieldSchema(StringName("long"), filter = true),
      LongListFieldSchema(StringName("longlist"), filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(
      List(
        IdField("_id", "1"),
        IntField("int", 1),
        LongField("long", 1),
        IntListField("intlist", List(1, 11)),
        LongListField("longlist", List(1, 11))
      )
    ),
    Document(
      List(
        IdField("_id", "2"),
        IntField("int", 2),
        LongField("long", 2),
        IntListField("intlist", List(2, 22)),
        LongListField("longlist", List(2, 22))
      )
    ),
    Document(
      List(
        IdField("_id", "3"),
        IntField("int", 3),
        LongField("long", 3),
        IntListField("intlist", List(3, 33)),
        LongListField("longlist", List(3, 33))
      )
    )
  )

  it should "select by int terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("int", 2)))))
      results shouldBe List("2")
    }
  }

  it should "select by int terms over lists" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("intlist", 2)))))
      results shouldBe List("2")
    }
  }

  it should "select by long terms" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("long", 2)))))
      results shouldBe List("2")
    }
  }
  it should "select by long terms over lists" in withIndex { index =>
    {
      val results = index.search(filters = Some(Filters(include = Some(TermPredicate("longlist", 2)))))
      results shouldBe List("2")
    }
  }
}
