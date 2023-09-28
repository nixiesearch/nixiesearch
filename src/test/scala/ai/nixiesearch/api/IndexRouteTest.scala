package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IntField, TextField}
import ai.nixiesearch.util.{LocalIndexFixture, TestIndexMapping}
import org.http4s.{Entity, EntityDecoder, Method, Request, Response, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import scodec.bits.ByteVector
import io.circe.syntax.*
import cats.effect.IO
import io.circe.{Decoder, Encoder}

import java.nio.file.Files

class IndexRouteTest extends AnyFlatSpec with Matchers with LocalIndexFixture {
  import IndexRoute.*
  import ai.nixiesearch.util.HttpTest.*

  val index = TestIndexMapping()

  it should "return index mapping on GET" in withStore(index) { store =>
    {
      val route = IndexRoute(store)
      val response =
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/test/_mapping"))).value.unsafeRunSync()
      response.map(_.status.code) shouldBe Some(200)
    }
  }

  it should "fail on 404" in withStore(index) { store =>
    {
      val route = IndexRoute(store)
      val response =
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/nope/_mapping"))).value.unsafeRunSync()
      response.map(_.status.code) shouldBe Some(404)
    }
  }

  it should "accept docs for existing indices" in withStore(index) { store =>
    {
      val doc = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      val response =
        send[Document, IndexResponse](IndexRoute(store).routes, "http://localhost/test/_index", Some(doc), Method.PUT)
      response.result shouldBe "created"
    }
  }

  it should "accept docs for new indices" in withStore(index) { store =>
    {
      val doc = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      val response =
        send[Document, IndexResponse](IndexRoute(store).routes, "http://localhost/test2/_index", Some(doc), Method.PUT)
      response.result shouldBe "created"
    }
  }

  it should "update dynamic mapping on new documents" in withStore { store =>
    {
      val doc1 = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      val response1 =
        send[Document, IndexResponse](IndexRoute(store).routes, "http://localhost/test2/_index", Some(doc1), Method.PUT)
      response1.result shouldBe "created"
      val doc2 = Document(List(TextField("_id", "1"), TextField("desc", "foo bar"), IntField("price", 10)))
      val response2 =
        send[Document, IndexResponse](IndexRoute(store).routes, "http://localhost/test2/_index", Some(doc2), Method.PUT)
      response2.result shouldBe "created"
    }
  }

  it should "flush empty index" in withStore { store =>
    {
      val response = sendRaw[String](IndexRoute(store).routes, "http://localhost/test2/_flush", None, Method.POST)
      response.map(_.status.code) shouldBe Some(404)
    }
  }

}
