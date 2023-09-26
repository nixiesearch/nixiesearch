package ai.nixiesearch.e2e

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.api.{IndexRoute, SearchRoute}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.IndexConfig.MappingConfig
import ai.nixiesearch.config.mapping.SearchType.HybridSearch
import ai.nixiesearch.config.mapping.{IndexConfig, IndexMapping}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.TestIndexRegistry
import cats.effect.IO
import org.http4s.{Entity, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector
import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterAll
import fs2.Stream

class DynamicMappingTest extends AnyFlatSpec with Matchers {
  import IndexRoute.given

  it should "create mapping on doc proper update" in {
    val registry  = TestIndexRegistry()
    val indexApi  = IndexRoute(registry)
    val searchApi = SearchRoute(registry)
    val response  = indexApi.index(Stream(Document(TextField("text", "hello world"))), "test").unsafeRunSync()
    response.result shouldBe "created"

    val mapping = indexApi.mapping("test").unsafeRunSync()

    mapping shouldBe Some(
      IndexMapping(
        name = "test",
        config = IndexConfig(MappingConfig(dynamic = true)),
        fields = Map(
          "_id"  -> TextFieldSchema("_id", filter = true),
          "text" -> TextFieldSchema("text", search = HybridSearch(), sort = true, facet = true, filter = true)
        )
      )
    )
  }
}
