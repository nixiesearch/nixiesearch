package ai.nixiesearch.api.query

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.KnnQuery
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticInferenceParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{TextField, TextListField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class KnnQueryMultiTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()
  val mapping                             = TestIndexMapping(
    "test",
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(name = StringName("category"), filter = true),
      TextListFieldSchema(
        name = StringName("titles"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"), dim=384)))
      )
    )
  )
  val docs = List(
    Document(
      List(TextField("_id", "1"), TextField("category", "clothing"), TextListField("titles", "red dress", "dress red"))
    ),
    Document(
      List(
        TextField("_id", "2"),
        TextField("category", "clothing"),
        TextListField("titles", "white dress", "dress white")
      )
    ),
    Document(
      List(
        TextField("_id", "3"),
        TextField("category", "sleepwear"),
        TextListField("titles", "red pajama", "pajama red")
      )
    )
  )

  it should "select matching documents for a knn query" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), TaskType.Query, "lady in red").unsafeRunSync()
      val docs = index.search(KnnQuery("titles", queryEmbed, Some(1)), n = 2)
      docs shouldBe List("1", "3")
    }
  }

  it should "filter knn results by category" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), TaskType.Query, "red").unsafeRunSync()
      val docs = index.search(
        KnnQuery("titles", queryEmbed, Some(10)),
        filters = Some(Filters(include = Some(TermPredicate("category", "clothing")))),
        n = 10
      )
      docs shouldBe List("1", "2")
    }
  }

}
