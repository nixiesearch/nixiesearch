package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.KnnQuery
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticInferenceParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, TextField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class KnnQueryLargeTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()
  val mapping                             = TestIndexMapping(
    "test",
    fields = List(
      IdFieldSchema(name = StringName("_id")),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"))))
      )
    )
  )
  val docs = List(
    Document(List(IdField("_id", "1"), TextField("title", "red dress", Some(randomEmbed())))),
    Document(List(IdField("_id", "2"), TextField("title", "white dress", Some(randomEmbed())))),
    Document(List(IdField("_id", "3"), TextField("title", "red pajama", Some(randomEmbed()))))
  )

  def randomEmbed(): Array[Float] = Array.fill[Float](4096)(Random.nextFloat())

  it should "select matching documents for a knn query" in withIndex { index =>
    {
      val docs = index.search(KnnQuery("title", randomEmbed(), Some(1)), n = 1)
      docs.size shouldBe 1
    }
  }
}
