package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.{RAGRequest, SearchRequest, SearchResponseFrame}
import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{SearchTest, TestIndexMapping, TestInferenceConfig}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class RAGTest extends SearchTest with Matchers {

  val mapping            = TestIndexMapping()
  override val inference = TestInferenceConfig.full()

  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )
  it should "do stream RAG" in withIndex { index =>
    {
      val request = SearchRequest(
        query = MatchQuery("title", "dress"),
        fields = List(StringName("title")),
        rag = Some(
          RAGRequest(
            prompt = "Shortly summarize the following search result document for a search query 'dress':\n\n",
            model = ModelRef("qwen2"),
            fields = List(StringName("title"))
          )
        )
      )
      val api = SearchRoute(index.searcher)
      val response = api
        .searchStreaming(request)
        .collect { case SearchResponseFrame.RAGResponseFrame(value) =>
          value
        }
        .compile
        .toList
        .unsafeRunSync()
      val text = response.map(_.token).mkString("")
      text shouldBe "The document describes dresses in two different colors: red and white."
    }
  }

  it should "do blocking RAG" in withIndex { index =>
    {
      val request = SearchRequest(
        query = MatchQuery("title", "dress"),
        fields = List(StringName("title")),
        rag = Some(
          RAGRequest(
            prompt = "Shortly summarize the following search result document for a search query 'dress':\n\n",
            model = ModelRef("qwen2"),
            fields = List(StringName("title"))
          )
        )
      )
      val api      = SearchRoute(index.searcher)
      val response = api.searchBlocking(request).unsafeRunSync()
      response.response shouldBe Some(
        "The document describes dresses in two different colors: red and white."
      )
    }
  }

  it should "limit num documents" in withIndex { index =>
    {
      val request = SearchRequest(
        query = MatchQuery("title", "dress"),
        fields = List(StringName("title")),
        rag = Some(
          RAGRequest(
            prompt = "Shortly summarize the following search result document for a search query 'dress':\n\n",
            model = ModelRef("qwen2"),
            fields = List(StringName("title")),
            topDocs = 1
          )
        )
      )
      val api      = SearchRoute(index.searcher)
      val response = api.searchBlocking(request).unsafeRunSync()
      response.response shouldBe Some(
        "The document is about a red dress, possibly for a specific event or occasion, and there are no other keywords or context provided. The document is titled \"red dress\", and there is no additional information or context given."
      )
    }
  }

  it should "trim docs" in withIndex { index =>
    {
      val request = SearchRequest(
        query = MatchQuery("title", "dress"),
        fields = List(StringName("title")),
        rag = Some(
          RAGRequest(
            prompt = "Shortly summarize the following search result document for a search query 'dress':\n\n",
            model = ModelRef("qwen2"),
            fields = List(StringName("title")),
            topDocs = 1,
            maxDocLength = 3
          )
        )
      )
      val api      = SearchRoute(index.searcher)
      val response = api.searchBlocking(request).unsafeRunSync()
      response.response shouldBe Some("Red dress trend.")
    }
  }
}
