package ai.nixiesearch.core.index.store

import ai.nixiesearch.api.IndexRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.FloatField
import ai.nixiesearch.core.search.Searcher
import ai.nixiesearch.util.{LocalNixieFixture, TestDocument, TestIndexMapping}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.MatchAllDocsQuery
import fs2.Stream

class LocalStoreTest extends AnyFlatSpec with Matchers with LocalNixieFixture {
  val mapping = TestIndexMapping()

  it should "open/close store" in withStore { store => {} }

  it should "open store and write/read doc" in withStore { store =>
    {
      store.updateMapping(mapping).unsafeRunSync()
      val route = IndexRoute(store)
      val doc   = TestDocument()
      route.index(Stream(doc), mapping.name).unsafeRunSync()
      route.flush(mapping.name).unsafeRunSync()
      val request = SearchRequest(MatchAllQuery(), fields = List("_id", "title", "price"))
      val index   = store.index(mapping.name).unsafeRunSync().get
      val docs    = Searcher.search(request, index).unsafeRunSync()
      docs.hits shouldBe List(Document(doc.fields :+ FloatField("_score", 1.0f)))
    }
  }

}
