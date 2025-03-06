package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.api.{IndexRoute, SearchRoute}
import ai.nixiesearch.config.{Config, InferenceConfig}
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.core.Document
import ai.nixiesearch.util.Tags.EndToEnd
import ai.nixiesearch.util.{DatasetLoader, SearchTest}
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.io.File
import io.circe.syntax.*
import org.http4s.{Entity, Method, Request, Uri}
import scodec.bits.ByteVector

class MSMarcoEndToEndTest extends AnyFlatSpec with Matchers with SearchTest {
  lazy val pwd     = System.getProperty("user.dir")
  lazy val conf    = Config.load(new File(s"$pwd/src/test/resources/config/msmarco.yml"), Map.empty).unsafeRunSync()
  lazy val mapping = conf.schema(IndexName.unsafe("msmarco"))
  lazy val docs    = DatasetLoader.fromFile(s"$pwd/src/test/resources/datasets/msmarco/msmarco.json", mapping, 1000)
  override def inference: InferenceConfig = conf.inference

  it should "load docs and search" taggedAs (EndToEnd.Index) in withIndex { nixie =>
    {
      val indexApi  = IndexRoute(nixie.indexer)
      val searchApi = SearchRoute(nixie.searcher)

      val jsonPayload = docs.map(doc => doc.asJson.noSpaces).mkString("\n")
      val indexRequest = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString("http://localhost:8080/msmarco/_index"),
        entity = Entity.strict(ByteVector.view(jsonPayload.getBytes()))
      )
      indexApi.index(indexRequest).unsafeRunSync()
      indexApi.flush().unsafeRunSync()
      nixie.searcher.sync().unsafeRunSync()

      val searchRequest = SearchRequest(MatchQuery("text", "manhattan"))
      val response      = searchApi.searchBlocking(searchRequest).unsafeRunSync()
      response.hits.size shouldBe 10
    }
  }
}
