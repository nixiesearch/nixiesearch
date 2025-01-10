package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class WildcardTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(FieldName.unsafe("_id"), filter = true),
      TextFieldSchema(FieldName.unsafe("title"), search = LexicalSearch()),
      TextFieldSchema(FieldName.unsafe("field_*"), search = LexicalSearch())
    )
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"), TextField("field_desc", "foo"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"), TextField("field_info", "foo"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama"), TextField("field_stuff", "foo")))
  )

  it should "accept and search wildcard fields" in withIndex { index =>
    {
      val docs = index.search(MultiMatchQuery("foo", List("field_desc", "field_info", "field_stuff"), Operator.OR))
      docs shouldBe List("3", "2", "1")
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
