package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.{KnnQuery, MatchQuery}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticInferenceParams, SemanticParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Query
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.util.Try

class KnnQueryTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()
  val mapping                             = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), dim=384)))
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
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), Query, "lady in red").unsafeRunSync()
      val docs = index.search(KnnQuery("title", queryEmbed, Some(1)), n = 1)
      docs shouldBe List("1")
    }
  }

  it should "fail on dim mismatch" in withIndex { index =>
    {
      val result = Try(index.search(KnnQuery("title", Array(0.0f, 0.0f), Some(1)), n = 1))
      result.isFailure shouldBe true
    }
  }

}
