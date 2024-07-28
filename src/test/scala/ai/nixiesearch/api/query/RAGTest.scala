package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.{RAGRequest, SearchRequest}
import ai.nixiesearch.config.mapping.RAGConfig
import ai.nixiesearch.config.mapping.RAGConfig.PromptTemplate.Qwen2Template
import ai.nixiesearch.config.mapping.RAGConfig.RAGModelConfig
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class RAGTest extends SearchTest with Matchers {

  val mapping = TestIndexMapping().copy(rag =
    RAGConfig(models =
      List(
        RAGModelConfig(
          handle = HuggingFaceHandle("Qwen", "Qwen2-0.5B-Instruct-GGUF", Some("qwen2-0_5b-instruct-q4_0.gguf")),
          prompt = Qwen2Template,
          name = "qwen2",
          system = None
        )
      )
    )
  )
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )
  it should "summarize search results in " in withIndex { index =>
    {
      val request = SearchRequest(
        query = MatchQuery("title", "dress"),
        fields = List("title"),
        rag = Some(
          RAGRequest(
            prompt = "summarize the following search result document for a search query 'dress':\n\n",
            model = "qwen2",
            fields = List("title")
          )
        )
      )
      val api      = SearchRoute(index.searcher)
      val response = api.searchStreaming(request).compile.toList.unsafeRunSync()
      val br       = 1
    }
  }
}
