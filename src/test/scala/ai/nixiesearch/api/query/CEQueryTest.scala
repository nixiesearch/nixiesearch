package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.rerank.CEQuery
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.OnnxRankInferenceModelConfig
import ai.nixiesearch.util.SearchTest
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.api.query.retrieve.{MatchQuery, SemanticQuery}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers

class CEQueryTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = InferenceConfig(
    ranker = Map(
      ModelRef("ce") -> OnnxRankInferenceModelConfig(
        model = HuggingFaceHandle("cross-encoder", "ms-marco-MiniLM-L6-v2")
      )
    ),
    embedding = Map(
      ModelRef("text") -> OnnxEmbeddingInferenceModelConfig(
        model = HuggingFaceHandle("nixiesearch", "e5-small-v2-onnx")
      )
    )
  )

  val mapping = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(
          semantic = Some(SemanticParams(model = ModelRef("text"))),
          lexical = Some(LexicalParams(analyze = English))
        )
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
      val docs =
        index.search(
          CEQuery(
            model = ModelRef("ce"),
            query = "red",
            retrieve = SemanticQuery("title", "lady in red", Some(3)),
            docTemplate = "{{ title }}",
            fields = Set("title")
          )
        )
      docs shouldBe List("1", "3", "2")
    }
  }
}
