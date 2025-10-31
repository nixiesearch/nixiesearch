package ai.nixiesearch.api.query.filter

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.BoolPredicate.{AndPredicate, OrPredicate}
import ai.nixiesearch.api.filter.Predicate.{RangePredicate, TermPredicate}
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.FiniteRange.Lower.Gt
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.FieldName.StringName

class BoolFilterTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      TextFieldSchema(StringName("color"), filter = true),
      FloatFieldSchema(StringName("price"), filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("color", "red"), FloatField("price", 10))),
    Document(List(TextField("_id", "2"), TextField("color", "white"), FloatField("price", 20))),
    Document(List(TextField("_id", "3"), TextField("color", "red"), FloatField("price", 30))),
    Document(List(TextField("_id", "4"), TextField("color", "white"), FloatField("price", 40)))
  )

  it should "select by both filters" in withIndex { index =>
    {
      val result = index.search(filters =
        Some(
          Filters(include = Some(AndPredicate(List(TermPredicate("color", "red"), RangePredicate("price", Gt(20))))))
        )
      )
      result shouldBe List("3")
    }
  }

  it should "do or" in withIndex { index =>
    {
      val result = index.search(filters =
        Some(Filters(include = Some(OrPredicate(List(TermPredicate("color", "red"), RangePredicate("price", Gt(30)))))))
      )
      result shouldBe List("1", "3", "4")
    }
  }
}
