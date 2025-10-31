package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.MatchQuery.Operator
import ai.nixiesearch.api.query.retrieve.MultiMatchQuery.BestFieldsQuery
import ai.nixiesearch.api.query.retrieve.{MatchAllQuery, MatchQuery, MultiMatchQuery}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.{FieldName, SearchParams}
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class WildcardTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(FieldName.unsafe("_id"), filter = true),
      TextFieldSchema(FieldName.unsafe("title"), search = SearchParams(lexical = Some(LexicalParams()))),
      TextFieldSchema(FieldName.unsafe("field_*"), search = SearchParams(lexical = Some(LexicalParams())))
    )
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"), TextField("field_desc", "foo"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"), TextField("field_info", "foo"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama"), TextField("field_stuff", "foo")))
  )

  it should "accept and search wildcard fields" in withIndex { index =>
    {
      val docs = index.search(BestFieldsQuery("foo", List(FieldName.unsafe("field_*"))))
      docs shouldBe List("1", "2", "3")
    }
  }

  it should "fetch wildcard fields" in withIndex { index =>
    {
      val docs   = index.searchRaw(MatchAllQuery(), fields = List("_id", "field_*"))
      val fields = docs.hits.flatMap(_.fields.filter(_.name.startsWith("field_")))
      fields shouldBe List(
        TextField("field_desc", "foo"),
        TextField("field_info", "foo"),
        TextField("field_stuff", "foo")
      )
    }
  }
}
