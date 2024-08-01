package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{RAGRequest, SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.IndexName
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
  lazy val pwd     = System.getProperty("user.dir")
  lazy val conf    = Config.load(new File(s"$pwd/src/test/resources/datasets/movies/config-rag.yaml")).unsafeRunSync()
  lazy val mapping = conf.schema(IndexName.unsafe("movies"))
  lazy val docs    = DatasetLoader.fromFile(s"$pwd/src/test/resources/datasets/movies/movies.jsonl.gz")

  it should "search" in withIndex { nixie =>
    {
      val searchApi = SearchRoute(nixie.searcher)

      val searchRequest = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8080/movies/_search"),
        entity = Entity.strict(
          ByteVector.view(
            SearchRequest(
              MatchQuery("title", "matrix"),
              fields = List("title", "overview"),
              rag = Some(
                RAGRequest(
                  topDocs = 3,
                  prompt =
                    "Based on following search resul documents, please summarize the answer for a user search query 'matrix'",
                  model = "qwen2",
                  fields = List("title", "overview")
                )
              )
            ).asJson.noSpaces.getBytes()
          )
        )
      )
      val response = searchApi.searchBlocking(searchRequest).unsafeRunSync().as[SearchResponse].unsafeRunSync()
      response.response shouldBe Some(
        "The matrix tells the story of a computer hacker who joins a group of underground insurgents fighting the vast and powerful computers who now rule the earth."
      )
    }
  }
}
