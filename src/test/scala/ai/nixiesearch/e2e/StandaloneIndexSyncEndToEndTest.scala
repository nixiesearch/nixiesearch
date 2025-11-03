package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse, SuggestRequest, SuggestResponse}
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.api.{IndexModifyRoute, SearchRoute}
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.config.{Config, InferenceConfig}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IdField, TextField}
import ai.nixiesearch.util.Tags.EndToEnd
import ai.nixiesearch.util.{DatasetLoader, EnvVars, SearchTest, TestDocument}
import cats.effect.IO
import org.http4s.{Entity, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scodec.bits.ByteVector
import io.circe.syntax.*

import java.io.File
import cats.effect.unsafe.implicits.global

class StandaloneIndexSyncEndToEndTest extends AnyFlatSpec with Matchers with SearchTest {
  lazy val pwd  = System.getProperty("user.dir")
  lazy val conf =
    Config.load(new File(s"$pwd/src/test/resources/datasets/movies/config.yaml"), EnvVars(Map.empty)).unsafeRunSync()
  lazy val mapping                        = conf.schema(IndexName.unsafe("movies"))
  val docs                                = Nil
  override def inference: InferenceConfig = conf.inference

  def makedoc(id: String, title: String): Document =
    Document(List(IdField("_id", id), TextField("title", title)))

  it should "load docs and search" taggedAs (EndToEnd.Index) in withIndex { nixie =>
    {

      def indexRequest(doc: Document) = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString("http://localhost:8080/v1/index/movies"),
        entity = Entity.strict(ByteVector.view(doc.asJson.noSpaces.getBytes()))
      )

      def searchRequest() = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8080/v1/movies/search"),
        entity = Entity.strict(
          ByteVector.view(
            SearchRequest(query = MatchAllQuery()).asJson.noSpaces.getBytes()
          )
        )
      )

      val indexApi  = IndexModifyRoute(nixie.indexer, Some(nixie.searcher))
      val searchApi = SearchRoute(nixie.searcher)

      indexApi.index(indexRequest(makedoc("1", "matrix 1"))).unsafeRunSync()
      indexApi.flush().unsafeRunSync()

      val response = searchApi.search(searchRequest()).unsafeRunSync().as[SearchResponse].unsafeRunSync()
      response.hits.size shouldBe 1

      indexApi.index(indexRequest(makedoc("2", "matrix 2"))).unsafeRunSync()
      indexApi.flush().unsafeRunSync()

      val response2 = searchApi.search(searchRequest()).unsafeRunSync().as[SearchResponse].unsafeRunSync()
      response2.hits.size shouldBe 2
    }
  }

}
