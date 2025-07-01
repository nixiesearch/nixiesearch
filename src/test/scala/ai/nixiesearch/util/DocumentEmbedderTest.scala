package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticSimpleParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.{EmbedModel, EmbedModelDict}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.util.DocumentEmbedderTest.MockEmbedModel
import cats.effect.IO
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.util.{Failure, Try}

class DocumentEmbedderTest extends AnyFlatSpec with Matchers {
  val modelRef         = ModelRef("test-model")
  val models           = EmbedModelDict(Map(modelRef -> new MockEmbedModel()), Metrics())
  val inferenceMapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = modelRef)))
      )
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val simpleMapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticSimpleParams(dim = 384)))
      )
    ),
    store = LocalStoreConfig(MemoryLocation())
  )

  it should "embed text field with defined model" in {
    val embedder = DocumentEmbedder(inferenceMapping, models)
    val doc      = Document(TextField("title", "test text"))
    val result   = embedder.embed(List(doc)).unsafeRunSync()

    val titleField = result.head.fields.collectFirst { case tf: TextField => tf }.get
    titleField.embedding should not be None
  }

  it should "skip pre-embedded fields" in {
    val embedder = DocumentEmbedder(inferenceMapping, models)
    val doc      = Document(TextField("title", "test text", Some(Array.fill(384)(0))))
    val result   = embedder.embed(List(doc)).unsafeRunSync()

    val titleField = result.head.fields.collectFirst { case tf: TextField => tf }.get
    titleField.embedding should not be None
  }

  it should "fail when no model defined, but field should be embedded in schema" in {
    val embedder = DocumentEmbedder(simpleMapping, models)
    val doc      = Document(TextField("title", "test text"))
    val result   = Try(embedder.embed(List(doc)).unsafeRunSync())
    result shouldBe a[Failure[UserError]]
  }
}

object DocumentEmbedderTest {
  class MockEmbedModel(dimensions: Int = 384) extends EmbedModel {
    override def model: String    = "mock-model"
    override def provider: String = "mock"
    override def batchSize: Int   = 32

    override def encode(task: TaskType, docs: List[String]): Stream[IO, Array[Float]] = {
      Stream.emits(docs.map(_ => Array.fill(dimensions)(0.0f)))
    }
  }

}
