package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName, SearchParams, SuggestSchema}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.index.IndexStats.{FieldStats, LeafStats, SegmentStats}
import ai.nixiesearch.util.{SearchTest, TestInferenceConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.language.implicitConversions
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.{LexicalParams, SemanticParams}

class IndexStatsTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(
        name = StringName("title"),
        search =
          SearchParams(lexical = Some(LexicalParams()), semantic = Some(SemanticParams(model = ModelRef("text")))),
        suggest = Some(SuggestSchema())
      )
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("title", "Hello world"))),
    Document(List(TextField("title", "foo bar"))),
    Document(List(TextField("title", "qux")))
  )
  override def inference: InferenceConfig = TestInferenceConfig.semantic()

  it should "return stats for simple indices" in withIndex(index => {
    val stats = IndexStats
      .fromIndex(index.indexer.index.directory, index.searcher.readersRef.get.unsafeRunSync().get.reader)
      .unsafeRunSync()
    stats.copy(luceneVersion = "1.0.0") shouldBe IndexStats(
      "1.0.0",
      List(SegmentStats("_0", 3, "Nixiesearch101", List("_0.cfe", "_0.si", "_0.cfs"), 0)),
      List(
        LeafStats(
          0,
          0,
          3,
          0,
          List(
            FieldStats(
              "title",
              0,
              false,
              "NONE",
              Map(
                "PerFieldKnnVectorsFormat.format" -> "Lucene99HnswVectorsFormat",
                "PerFieldKnnVectorsFormat.suffix" -> "0"
              ),
              384,
              "FLOAT32",
              "COSINE"
            ),
            FieldStats(
              "title$suggest",
              1,
              true,
              "DOCS_AND_FREQS_AND_POSITIONS",
              Map("PerFieldPostingsFormat.format" -> "Completion101", "PerFieldPostingsFormat.suffix" -> "0"),
              0,
              "FLOAT32",
              "EUCLIDEAN"
            )
          )
        )
      )
    )
  })
}
