package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticSimpleParams}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field.{TextField, TextListField}
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
        search = SearchParams(semantic = Some(SemanticSimpleParams()))
      )
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val listInferenceMapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextListFieldSchema(
        name = StringName("tags"),
        search = SearchParams(semantic = Some(SemanticInferenceParams(model = modelRef)))
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

  it should "embed text list field with no embeddings provided" in {
    val embedder = DocumentEmbedder(listInferenceMapping, models)
    val doc      = Document(TextListField("tags", List("scala", "functional", "programming")))
    val result   = embedder.embed(List(doc)).unsafeRunSync()

    val tagsField = result.head.fields.collectFirst { case tlf: TextListField => tlf }.get
    tagsField.embeddings should not be None
    tagsField.embeddings.get should have size 3
    tagsField.embeddings.get.foreach(embedding => embedding should have length 384)
  }

  it should "skip pre-embedded text list fields" in {
    val embedder      = DocumentEmbedder(listInferenceMapping, models)
    val preEmbeddings = List(Array.fill(384)(1.0f), Array.fill(384)(2.0f), Array.fill(384)(3.0f))
    val doc           = Document(TextListField("tags", List("scala", "functional", "programming"), Some(preEmbeddings)))
    val result        = embedder.embed(List(doc)).unsafeRunSync()

    val tagsField = result.head.fields.collectFirst { case tlf: TextListField => tlf }.get
    tagsField.embeddings should not be None
    tagsField.embeddings.get should have size 3
    tagsField.embeddings.get(0)(0) shouldBe 1.0f
    tagsField.embeddings.get(1)(0) shouldBe 2.0f
    tagsField.embeddings.get(2)(0) shouldBe 3.0f
  }

  it should "embed text list field with multiple embeddings per single string" in {
    val embedder    = DocumentEmbedder(listInferenceMapping, models)
    val multiEmbeds = List(Array.fill(384)(1.0f), Array.fill(384)(2.0f), Array.fill(384)(3.0f))
    val doc         = Document(TextListField("tags", List("scala"), Some(multiEmbeds)))
    val result      = embedder.embed(List(doc)).unsafeRunSync()

    val tagsField = result.head.fields.collectFirst { case tlf: TextListField => tlf }.get
    tagsField.embeddings should not be None
    tagsField.embeddings.get should have size 3
    tagsField.value should have size 1
    tagsField.value.head shouldBe "scala"
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
