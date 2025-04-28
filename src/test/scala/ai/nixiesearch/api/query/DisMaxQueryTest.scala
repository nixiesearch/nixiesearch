package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.{BoolQuery, DisMaxQuery, MatchQuery}
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class DisMaxQueryTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(lexical = Some(LexicalParams()))
      ),
      TextFieldSchema(
        name = StringName("desc"),
        search = SearchParams(lexical = Some(LexicalParams()))
      )
    )
  )
  val docs = List(
    Document(
      List(
        TextField("_id", "1"),
        TextField("title", "red dress"),
        TextField(
          "desc",
          "Turn heads in this elegant red dress, perfect for any occasion with its flattering fit and timeless style."
        )
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("title", "white dress"),
        TextField(
          "desc",
          "Turn heads in this elegant white dress, perfect for any occasion with its timeless silhouette and effortlessly chic style."
        )
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("title", "red pajama"),
        TextField(
          "desc",
          "Cozy up in style with our ultra-soft Red Pajama, designed for all-night comfort and a bold, vibrant look."
        )
      )
    )
  )

  it should "select docs with must" in withIndex { index =>
    {
      val docs = index.search(DisMaxQuery(List(MatchQuery("title", "dress"), MatchQuery("desc", "dress"))))
      docs shouldBe List("1", "2")
    }
  }
}
