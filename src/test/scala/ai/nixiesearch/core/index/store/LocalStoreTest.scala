package ai.nixiesearch.core.index.store

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.search.Searcher
import ai.nixiesearch.util.{IndexFixture, TestDocument, TestIndexMapping}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.MatchAllDocsQuery

class LocalStoreTest extends AnyFlatSpec with Matchers with IndexFixture {
  val index = TestIndexMapping()

  it should "open/close store" in withStore { store => {} }

  it should "open store and write/read doc" in withStore { store =>
    {
      val writer = store.writer(index).unsafeRunSync()
      val doc    = TestDocument()
      writer.addDocuments(List(doc)).unsafeRunSync()
      writer.writer.commit()
      val readerMaybe = store.reader(index.name).unsafeRunSync()
      readerMaybe.isDefined shouldBe true
      val reader  = readerMaybe.get
      val request = SearchRequest(MatchAllQuery(), fields = NonEmptyList.of("_id", "title", "price"))
      val docs    = Searcher.search(request, reader).unsafeRunSync()
      docs.hits shouldBe List(doc)
    }
  }

  it should "refresh the mapping" in withStore(index) { store =>
    {
      val writer = store.writer(index).unsafeRunSync()
      val doc    = TestDocument()
      writer.addDocuments(List(doc)).unsafeRunSync()
      writer.writer.commit()
      val updatedMapping = index.copy(fields = index.fields ++ Map("desc" -> TextFieldSchema("desc")))
      writer.refreshMapping(updatedMapping).unsafeRunSync()
      val retrievedMapping = store.mapping(index.name).unsafeRunSync()
      retrievedMapping shouldBe Some(updatedMapping)
    }
  }
}
