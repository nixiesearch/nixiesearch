package ai.nixiesearch.api.query.sort

import ai.nixiesearch.api.SearchRoute.SortPredicate.FieldValueSort
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.Last
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.{ASC, DESC}
import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.LexicalParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class ScoreSortTest extends SearchTest with Matchers {
  val name    = StringName("field")
  val mapping =
    TestIndexMapping(
      "sort",
      List(
        TextFieldSchema(name, search = SearchParams(lexical = Some(LexicalParams()))),
        TextFieldSchema(StringName("_id"))
      )
    )
  val docs = List(
    Document(List(TextField("_id", "miss"))),
    Document(List(TextField("_id", "0"), TextField("field", "In the town where I was born"))),
    Document(List(TextField("_id", "1"), TextField("field", "Lived a man who sailed to sea"))),
    Document(List(TextField("_id", "2"), TextField("field", "And he told us of his life"))),
    Document(List(TextField("_id", "3"), TextField("field", "In the land of submarines"))),
    Document(List(TextField("_id", "4"), TextField("field", "So we sailed on to the sun"))),
    Document(List(TextField("_id", "5"), TextField("field", "′Til we found a sea of green")))
  )

  it should "sort by score desc" in withIndex { index =>
    {
      val results = index.search(
        query = MatchQuery("field", "sailed sun sea"),
        sort = List(FieldValueSort(StringName("_score"), order = DESC, missing = Last))
      )
      results shouldBe List("4", "1", "5")
    }
  }

  it should "sort by score asc" in withIndex { index =>
    {
      val results = index.search(
        query = MatchQuery("field", "sailed sun sea"),
        sort = List(FieldValueSort(StringName("_score"), order = ASC, missing = Last))
      )
      results shouldBe List("5", "1", "4")
    }
  }

}
