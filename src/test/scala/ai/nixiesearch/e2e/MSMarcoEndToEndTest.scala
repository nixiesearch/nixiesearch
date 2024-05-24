package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.api.{IndexRoute, SearchRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.core.Document
import ai.nixiesearch.util.SearchTest
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import com.github.luben.zstd.ZstdInputStream

import java.io.{File, FileInputStream}
import java.nio.file.Files
import fs2.Stream
import fs2.io.readInputStream
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.{Entity, Method, Request, Uri}
import scodec.bits.ByteVector

class MSMarcoEndToEndTest extends AnyFlatSpec with Matchers with SearchTest {
  lazy val pwd     = System.getProperty("user.dir")
  lazy val conf    = Config.load(new File(s"$pwd/src/test/resources/config/msmarco.yml")).unsafeRunSync()
  lazy val mapping = conf.schema("msmarco")
  lazy val docs = readInputStream[IO](
    IO(new FileInputStream(new File(s"$pwd/src/test/resources/datasets/msmarco/msmarco.json"))),
    1024000
  ).through(fs2.text.utf8.decode)
    .through(fs2.text.lines)
    .filter(_.nonEmpty)
    .parEvalMapUnordered(8)(line =>
      IO(decode[Document](line)).flatMap {
        case Left(value)  => IO.raiseError(value)
        case Right(value) => IO.pure(value)
      }
    )
    .take(1000)
    .compile
    .toList
    .unsafeRunSync()

  it should "load docs and search" in withIndex { nixie =>
    {
      val indexApi  = IndexRoute(nixie.indexer)
      val searchApi = SearchRoute(nixie.searcher)

      val jsonPayload = docs.map(doc => doc.asJson.noSpaces).mkString("\n")
      val indexRequest = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString("http://localhost:8080/msmarco/_index"),
        entity = Entity.strict(ByteVector.view(jsonPayload.getBytes()))
      )
      indexApi.index(indexRequest, "msmarco").unsafeRunSync()
      indexApi.flush("msmarco").unsafeRunSync()
      nixie.searcher.sync().unsafeRunSync()

      val searchRequest = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8080/msmarco/_search"),
        entity =
          Entity.strict(ByteVector.view(SearchRequest(MatchQuery("text", "manhattan")).asJson.noSpaces.getBytes()))
      )
      val response = searchApi.search(searchRequest).unsafeRunSync()
      response.as[SearchResponse].map(_.hits.size).unsafeRunSync() shouldBe 10
    }
  }
}
