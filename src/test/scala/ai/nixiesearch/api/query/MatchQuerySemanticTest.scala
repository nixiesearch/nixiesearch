package ai.nixiesearch.api.query

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, NoSearch}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers

class MatchQuerySemanticTest extends SearchTest with Matchers {
  override lazy val inference = TestInferenceConfig.semantic()
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = HybridSearch(ModelRef("text"))),
      TextListFieldSchema(name = "cat", search = NoSearch, filter = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"), TextListField("cat", "a", "b"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"), TextListField("cat", "b", "c"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama"), TextListField("cat", "c", "a")))
  )

  it should "search for similar docs" in withIndex { index =>
    {
      val response = index.search(MatchQuery("title", "white pajama"))
      response shouldBe List("3", "2", "1")
    }
  }

  it should "search and filter" in withIndex { index =>
    {
      val response =
        index.search(
          MatchQuery("title", "white pajama"),
          filters = Some(Filters(include = Some(TermPredicate("cat", "b"))))
        )
      response shouldBe List("2", "1")
    }
  }
}
