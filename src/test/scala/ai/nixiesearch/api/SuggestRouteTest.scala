package ai.nixiesearch.api

import ai.nixiesearch.api.SuggestRoute.{SuggestRequest, SuggestResponse}
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.mapping.SuggestMapping.SUGGEST_FIELD
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestMapping}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.util.IndexFixture
import org.http4s.{Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SuggestRouteTest extends AnyFlatSpec with Matchers with IndexFixture {
  import ai.nixiesearch.util.HttpTest.*
  import IndexRoute.*

  val mapping = IndexMapping(
    name = "test",
    fields = Map(
      SuggestMapping.SUGGEST_FIELD -> TextFieldSchema(
        name = SuggestMapping.SUGGEST_FIELD,
        search = SemanticSearch(model = HuggingFaceHandle("nixiesearch", "nixie-suggest-small-v1"))
      )
    )
  )

  it should "return index mapping on GET" in withStore(mapping) { store =>
    {
      val route = IndexRoute(store)
      val response =
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/test/_mapping"))).value.unsafeRunSync()
      response.map(_.status.code) shouldBe Some(200)
    }
  }

  it should "accept docs for existing indices" in withStore(mapping) { store =>
    {
      val response =
        send[Document, IndexResponse](
          IndexRoute(store).routes,
          "http://localhost/test/_index",
          Some(Document(List(TextField(SUGGEST_FIELD, "hello")))),
          Method.PUT
        )
      response.result shouldBe "created"
    }
  }

  it should "autocomplete" in withStore(mapping) { store =>
    {
      val docs = List(
        Document(List(TextField(SUGGEST_FIELD, "hello"))),
        Document(List(TextField(SUGGEST_FIELD, "help"))),
        Document(List(TextField(SUGGEST_FIELD, "hip hop")))
      )
      val indexResponse =
        send[List[Document], IndexResponse](
          IndexRoute(store).routes,
          "http://localhost/test/_index",
          Some(docs),
          Method.PUT
        )
      indexResponse.result shouldBe "created"

      val flushResponse = sendRaw[String](IndexRoute(store).routes, "http://localhost/test/_flush", None, Method.POST)

      val searchResponse =
        send[SuggestRequest, SuggestResponse](
          SuggestRoute(store).routes,
          "http://localhost/test/_suggest",
          Some(SuggestRequest("hel", 10)),
          Method.POST
        )
      searchResponse.suggestions.map(_.text) shouldBe List("help", "hello", "hip hop")
    }
  }

}
