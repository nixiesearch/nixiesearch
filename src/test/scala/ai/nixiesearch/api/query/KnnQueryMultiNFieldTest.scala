package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.retrieve.KnnQuery
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticInferenceParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.{TextField, TextListField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class KnnQueryMultiNFieldTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()

  val mapping = TestIndexMapping(
    "testmulti",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextListFieldSchema(
        name = StringName("songs"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"))))
      ),
      TextListFieldSchema(
        name = StringName("categories"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"))))
      )
    )
  )

  val docs = List(
    Document(
      List(
        TextField("_id", "1"),
        TextListField("songs", "bohemian rhapsody", "we will rock you"),
        TextListField("categories", "rock", "classic rock")
      )
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextListField("songs", "billie jean", "thriller"),
        TextListField("categories", "pop", "dance")
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextListField("songs", "stairway to heaven", "whole lotta love"),
        TextListField("categories", "rock", "hard rock")
      )
    )
  )

  it should "search over songs field" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), TaskType.Query, "queen rock").unsafeRunSync()
      val results = index.search(KnnQuery("songs", queryEmbed, Some(2)), n = 2)
      results should contain("1")
    }
  }

  it should "search over categories field" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding
          .encode(ModelRef("text"), TaskType.Query, "dance music")
          .unsafeRunSync()
      val results = index.search(KnnQuery("categories", queryEmbed, Some(2)), n = 2)
      results should contain("2")
    }
  }

  it should "return different results for different fields" in withIndex { index =>
    {
      val songQuery =
        index.indexer.index.models.embedding.encode(ModelRef("text"), TaskType.Query, "queen").unsafeRunSync()
      val categoryQuery =
        index.indexer.index.models.embedding.encode(ModelRef("text"), TaskType.Query, "pop").unsafeRunSync()

      val songResults     = index.search(KnnQuery("songs", songQuery, Some(1)), n = 3)
      val categoryResults = index.search(KnnQuery("categories", categoryQuery, Some(1)), n = 3)

      songResults.head shouldBe "1"
      categoryResults.head shouldBe "2"
    }
  }

}
