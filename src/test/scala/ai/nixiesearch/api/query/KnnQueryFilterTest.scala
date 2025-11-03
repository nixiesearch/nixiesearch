package ai.nixiesearch.api.query

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import ai.nixiesearch.api.query.retrieve.KnnQuery
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntListFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams
import ai.nixiesearch.config.mapping.SearchParams.SemanticInferenceParams
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, IntListField, TextField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Query
import cats.effect.unsafe.implicits.global

import scala.util.Try

class KnnQueryFilterTest extends SearchTest with Matchers {
  override def inference: InferenceConfig = TestInferenceConfig.semantic()
  val mapping                             = TestIndexMapping(
    "test",
    fields = List(
      IdFieldSchema(name = StringName("_id")),
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"))))
      ),
      TextFieldSchema(name = StringName("tag"), filter = true),
      IntListFieldSchema(name = StringName("iltag"), filter = true)
    )
  )
  val docs = List(
    Document(
      List(
        IdField("_id", "1"),
        TextField("title", "red dress"),
        TextField("tag", "a"),
        IntListField("iltag", List(1, 2))
      )
    ),
    Document(
      List(
        IdField("_id", "2"),
        TextField("title", "white dress"),
        TextField("tag", "a"),
        IntListField("iltag", List(2, 3))
      )
    ),
    Document(
      List(
        IdField("_id", "3"),
        TextField("title", "red pajama"),
        TextField("tag", "b"),
        IntListField("iltag", List(3, 4))
      )
    )
  )
  it should "select matching documents for a knn query with str filter" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), Query, "lady in red").unsafeRunSync()
      val docs = index.search(
        query = KnnQuery("title", queryEmbed, Some(3)),
        filters = Some(
          Filters(
            include = Some(TermPredicate("tag", "a"))
          )
        ),
        n = 3
      )
      docs shouldBe List("1", "2")
    }
  }
  it should "select matching documents for a knn query with int list filter" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), Query, "lady in red").unsafeRunSync()
      val docs = index.search(
        query = KnnQuery("title", queryEmbed, Some(3)),
        filters = Some(
          Filters(
            include = Some(TermPredicate("iltag", 3))
          )
        ),
        n = 3
      )
      docs shouldBe List("3", "2")
    }
  }
  it should "fail on filter field mismatch" in withIndex { index =>
    {
      val queryEmbed =
        index.indexer.index.models.embedding.encode(ModelRef("text"), Query, "lady in red").unsafeRunSync()
      val docs = Try(
        index.search(
          query = KnnQuery("title", queryEmbed, Some(3)),
          filters = Some(
            Filters(
              include = Some(TermPredicate("tagssss", "a"))
            )
          ),
          n = 3
        )
      )
      docs.isFailure shouldBe true
    }
  }
}
