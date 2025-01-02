package ai.nixiesearch.api.query

import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.{SearchTest, TestIndexMapping}
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class UpdateDeleteTest extends SearchTest with Matchers {
  val mapping = TestIndexMapping()
  val docs = List(
    Document(List(TextField("_id", "1"), TextField("title", "red dress"))),
    Document(List(TextField("_id", "2"), TextField("title", "white dress"))),
    Document(List(TextField("_id", "3"), TextField("title", "red pajama")))
  )
  it should "delete documents" in withIndex { index =>
    {
      index.search(MatchQuery("title", "pajama")) shouldBe List("3")
      index.indexer.delete("3").unsafeRunSync()
      index.indexer.flush().unsafeRunSync()
      index.indexer.index.sync().unsafeRunSync()
      index.searcher.sync().unsafeRunSync()
      index.search(MatchQuery("title", "pajama")) shouldBe List()
    }
  }

  it should "update documents" in withIndex { index =>
    {
      index.search(MatchQuery("title", "pajama")) shouldBe List("3")
      index.indexer
        .addDocuments(List(Document(List(TextField("_id", "3"), TextField("title", "red jacket")))))
        .unsafeRunSync()
      index.indexer.flush().unsafeRunSync()
      index.indexer.index.sync().unsafeRunSync()
      index.searcher.sync().unsafeRunSync()
      index.search(MatchQuery("title", "pajama")) shouldBe List()
      index.search(MatchQuery("title", "jacket")) shouldBe List("3")
    }
  }

}