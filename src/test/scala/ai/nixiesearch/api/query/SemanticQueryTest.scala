package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.{KnnQuery, SemanticQuery}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers

class SemanticQueryTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()

  val mapping = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticParams(model = ModelRef("text"))))
      )
    )
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )

  it should "select matching documents for a knn query" in withIndex { index =>
    {
      val docs = index.search(SemanticQuery("title", "lady in red", Some(1)))
      docs shouldBe List("1")
    }
  }

}
