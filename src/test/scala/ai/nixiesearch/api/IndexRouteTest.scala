package ai.nixiesearch.api

import ai.nixiesearch.api.IndexRoute.IndexResponse
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{IntField, TextField}
import ai.nixiesearch.util.{LocalNixie, SearchTest, TestIndexMapping}
import org.http4s.{Entity, EntityDecoder, Method, Request, Response, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import scodec.bits.ByteVector
import io.circe.syntax.*
import cats.effect.IO
import io.circe.{Decoder, Encoder}

import java.nio.file.Files

class IndexRouteTest extends AnyFlatSpec with Matchers with SearchTest {
  import IndexRoute.*
  import ai.nixiesearch.util.HttpTest.*
  val docs    = Nil
  val mapping = TestIndexMapping()

  it should "return index mapping on GET" in withIndex { store =>
    {
      val route = IndexRoute(store.indexer)
      val response =
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/test/_mapping"))).value.unsafeRunSync()
      response.map(_.status.code) shouldBe Some(200)
    }
  }

  it should "fail on 404" in withIndex { store =>
    {
      val route = IndexRoute(store.indexer)
      an[NullPointerException] should be thrownBy {
        route.routes(Request(uri = Uri.unsafeFromString("http://localhost/nope/_mapping"))).value.unsafeRunSync()
      }
    }
  }

  it should "accept docs for existing indices" in withIndex { store =>
    {
      val doc = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      val response =
        send[Document, IndexResponse](
          IndexRoute(store.indexer).routes,
          "http://localhost/test/_index",
          Some(doc),
          Method.PUT
        )
      response.result shouldBe "created"
    }
  }

  it should "not accept docs for new indices" in withIndex { store =>
    {
      val doc = Document(List(TextField("_id", "1"), TextField("title", "foo bar"), IntField("price", 10)))
      an[NullPointerException] should be thrownBy {
        send[Document, IndexResponse](
          IndexRoute(store.indexer).routes,
          "http://localhost/test2/_index",
          Some(doc),
          Method.PUT
        )
      }
    }
  }

}
