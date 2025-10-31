package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.SemanticQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.QuantStore.*
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers

class SemanticQuantizationTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()

  val mapping = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title_f32"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), quantize = Float32, dim=384)))
      ),
      TextFieldSchema(
        name = StringName("title_i8"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), quantize = Int8, dim=384)))
      ),
      TextFieldSchema(
        name = StringName("title_i4"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), quantize = Int4, dim=384)))
      ),
      TextFieldSchema(
        name = StringName("title_i1"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), quantize = Int1, dim=384)))
      )
    )
  )
  val fields = List("title_f32", "title_i8", "title_i4", "title_i1")
  val docs   = List(
    Document(List(TextField("_id", "1")) ++ fields.map(f => TextField(f, "red dress"))),
    Document(List(TextField("_id", "2")) ++ fields.map(f => TextField(f, "white dress"))),
    Document(List(TextField("_id", "3")) ++ fields.map(f => TextField(f, "red pajama")))
  )

  it should "search for f32 query" in withIndex { index =>
    {
      val docs = index.search(SemanticQuery("title_f32", "lady in red", Some(1)), n = 1)
      docs shouldBe List("1")
    }
  }

  it should "search for i8 query" in withIndex { index =>
    {
      val docs = index.search(SemanticQuery("title_i8", "lady in red", Some(1)), n = 1)
      docs shouldBe List("1")
    }
  }
  it should "search for i4 query" in withIndex { index =>
    {
      val docs = index.search(SemanticQuery("title_i4", "lady in red", Some(1)), n = 1)
      docs shouldBe List("1")
    }
  }
  it should "search for i1 query" in withIndex { index =>
    {
      val docs = index.search(SemanticQuery("title_i1", "lady in red", Some(1)), n = 1)
      docs shouldBe List("1")
    }
  }

}
