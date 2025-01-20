package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{RAGRequest, SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{DatasetLoader, SearchTest}
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import io.circe.syntax.*
import org.http4s.{Entity, Method, Request, Uri}
import scodec.bits.ByteVector

import java.io.File

class RAGEndToEndTest extends AnyFlatSpec with Matchers with SearchTest {
  lazy val pwd = System.getProperty("user.dir")
  lazy val conf =
    Config.load(new File(s"$pwd/src/test/resources/datasets/movies/config-rag.yaml"), Map.empty).unsafeRunSync()
  lazy val mapping       = conf.schema(IndexName.unsafe("movies"))
  lazy val docs          = DatasetLoader.fromFile(s"$pwd/src/test/resources/datasets/movies/movies.jsonl.gz", mapping)
  override def inference = conf.inference

  it should "search" in withIndex { nixie =>
    {
      val searchApi = SearchRoute(nixie.searcher)

      val searchRequest = SearchRequest(
        MatchQuery("title", "matrix"),
        fields = List(StringName("title"), StringName("overview")),
        rag = Some(
          RAGRequest(
            topDocs = 3,
            prompt =
              "Based on following search result documents, summarize the answer for a user search query 'matrix' in a single sentence. Do not include task or intro.",
            model = ModelRef("qwen2"),
            fields = List(StringName("title"), StringName("overview"))
          )
        )
      )
      val response = searchApi.searchBlocking(searchRequest).unsafeRunSync()
      response.response shouldBe Some(
        "The Matrix is a science fiction film set in the 22nd century about a computer hacker who joins a group of underground insurgents fighting the powerful computers that rule the earth."
      )
    }
  }
}
