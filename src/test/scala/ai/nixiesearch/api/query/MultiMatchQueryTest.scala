package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers

class MultiMatchQueryTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = "_id"),
      TextFieldSchema(name = "title", search = LexicalSearch(), sort = true),
      TextFieldSchema(name = "desc", search = LexicalSearch(), sort = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "dress"), TextField("desc", "red"))),
    Document(List(TextField("_id", "2"), TextField("title", "dress"), TextField("desc", "white"))),
    Document(List(TextField("_id", "3"), TextField("title", "pajama"), TextField("desc", "red")))
  )

  it should "select matching documents for a single-term query" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("pajama", List("title", "desc")))
      docs shouldBe List("3")
    }
  }

  it should "select docs for a multi-term query and AND" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("white pajama", List("title", "desc"), Operator.AND))
      docs shouldBe Nil
    }
  }

  it should "select docs for a multi-term query and OR" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("white pajama", List("title", "desc"), Operator.OR))
      docs shouldBe List("3", "2")
    }
  }

}
