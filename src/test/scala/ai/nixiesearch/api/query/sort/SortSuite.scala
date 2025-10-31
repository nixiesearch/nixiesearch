package ai.nixiesearch.api.query.sort

import ai.nixiesearch.api.SearchRoute.SortPredicate.FieldValueSort
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.{First, Last}
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.{ASC, DESC}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.{Document, Field}
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait SortSuite[T <: Field, S <: FieldSchema[T], U: Ordering] extends SearchTest with Matchers {
  def field(name: FieldName, value: U): T
  def schema(name: FieldName): S
  def values: List[U]

  val name    = StringName("field")
  val mapping = TestIndexMapping("sort", List(schema(name), TextFieldSchema(StringName("_id"))))
  val docs    = List(
    Document(
      List(
        TextField("_id", "miss")
      )
    )
  ) ++ values.zipWithIndex.map((value, id) => Document(List(TextField("_id", id.toString), field(name, value))))

  it should "sort by value asc, missing last" in withIndex { index =>
    {
      val docs = index.search(sort = List(FieldValueSort(name, ASC, Last)))
      docs shouldBe values.zipWithIndex.sortBy(_._1).map(_._2).map(_.toString) :+ "miss"
    }
  }

  it should "sort by value asc, missing first" in withIndex { index =>
    {
      val docs = index.search(sort = List(FieldValueSort(name, ASC, First)))
      docs shouldBe "miss" +: values.zipWithIndex.sortBy(_._1).map(_._2).map(_.toString)
    }
  }

  it should "sort by value desc, missing first" in withIndex { index =>
    {
      val docs = index.search(sort = List(FieldValueSort(name, DESC, First)))
      docs shouldBe "miss" +: values.zipWithIndex.sortBy(_._1).reverse.map(_._2).map(_.toString)
    }
  }

  it should "sort by value desc, missing last" in withIndex { index =>
    {
      val docs = index.search(sort = List(FieldValueSort(name, DESC, Last)))
      docs shouldBe values.zipWithIndex.sortBy(_._1).reverse.map(_._2).map(_.toString) :+ "miss"
    }
  }

}
