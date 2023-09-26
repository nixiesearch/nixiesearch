package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchQuery
import ai.nixiesearch.api.{IndexRoute, SearchRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.core.Document
import ai.nixiesearch.index.IndexRegistry
import ai.nixiesearch.util.TestIndexRegistry
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

class MSMarcoEndToEndTest extends AnyFlatSpec with Matchers {
  it should "load docs and search" in {
    val pwd = System.getProperty("user.dir")
    val conf = Config.load(Some(new File(s"$pwd/src/test/resources/config/msmarco.yml"))).unsafeRunSync()
    val registry = TestIndexRegistry(conf.search.values.toList)

    val indexApi  = IndexRoute(registry)
    val searchApi = SearchRoute(registry)

    val docs = readInputStream[IO](
      IO(
        new ZstdInputStream(
          new FileInputStream(new File(s"$pwd/src/test/resources/datasets/msmarco/corpus-10k.jsonl.zst"))
        )
      ),
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

    val jsonPayload = docs.map(doc => doc.asJson.noSpaces).mkString("\n")
    val indexRequest = Request[IO](
      method = Method.PUT,
      uri = Uri.unsafeFromString("http://localhost:8080/msmarco/_index"),
      entity = Entity.strict(ByteVector.view(jsonPayload.getBytes()))
    )
    indexApi.handleIndex(indexRequest, "msmarco").unsafeRunSync()
    indexApi.flush("msmarco").unsafeRunSync()

    val searchRequest = SearchRequest(MatchQuery("text", "manhattan"))
    val reader        = registry.reader("msmarco").unsafeRunSync().get
    val response      = searchApi.searchDsl(searchRequest, reader).unsafeRunSync()
    response.hits.size shouldBe 10
  }
}
