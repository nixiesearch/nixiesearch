package ai.nixiesearch.api.query.sort

import ai.nixiesearch.api.SearchRoute.SortPredicate.FieldValueSort
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.Last
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.{ASC, DESC}
import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers

class DocScoreTest extends SearchTest with Matchers {
  val name = StringName("field")
  val mapping =
    TestIndexMapping("sort", List(TextFieldSchema(StringName("_id"))))
  val docs = List(
    Document(List(TextField("_id", "0"))),
    Document(List(TextField("_id", "1"))),
    Document(List(TextField("_id", "2"))),
    Document(List(TextField("_id", "3"))),
    Document(List(TextField("_id", "4"))),
    Document(List(TextField("_id", "5")))
  )

  it should "sort by docid desc" in withIndex { index =>
    {
      val results = index.search(
        sort = List(FieldValueSort(StringName("_doc"), order = DESC, missing = Last))
      )
      results shouldBe List("5", "4", "3", "2", "1", "0")
    }
  }

  it should "sort by score asc" in withIndex { index =>
    {
      val results = index.search(
        sort = List(FieldValueSort(StringName("_doc"), order = ASC, missing = Last))
      )
      results shouldBe List("0", "1", "2", "3", "4", "5")
    }
  }

}
