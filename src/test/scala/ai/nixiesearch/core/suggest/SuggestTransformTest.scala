package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.SuggestMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.util.SearchTest
import SuggestMapping.{SUGGEST_FIELD, Transform}
import ai.nixiesearch.core.Field.{IntField, TextField, TextListField}
import fs2.Stream
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.{IndexSearcher, MatchAllDocsQuery}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SuggestTransformTest extends AnyFlatSpec with Matchers with SearchTest {
  val suggest = SuggestMapping(name = "test")
  val mapping = suggest.index

  val docs = List(
    Document(List(TextField(SUGGEST_FIELD, "hello"))),
    Document(List(TextField(SUGGEST_FIELD, "hello world")))
  )

  it should "generate unique suggestions" in new Index {
    val conf   = Transform(List("title"))
    val source = List(Document(TextField("title", "foo bar baz")))
    val result = generate(index.searcherRef.get.unsafeRunSync(), Some(conf), source)
    result shouldBe List("foo", "bar", "baz", "foo bar", "bar baz", "foo bar baz")
  }

  it should "skip existing" in new Index {
    val conf   = Transform(List("title"))
    val source = List(Document(TextField("title", "hello world")))
    val result = generate(index.searcherRef.get.unsafeRunSync(), Some(conf), source)
    result shouldBe List("world")
  }

  it should "skip wrong types" in new Index {
    val conf   = Transform(List("title"))
    val source = List(Document(IntField("t", 1), TextListField("title", List("hello world"))))
    val result = generate(index.searcherRef.get.unsafeRunSync(), Some(conf), source)
    result shouldBe List("world")
  }

  def generate(s: IndexSearcher, conf: Option[Transform], docs: List[Document]): List[String] = {
    val result = Stream
      .emits(docs)
      .through(SuggestTransform.doc2suggest(conf, s))
      .flatMap(doc =>
        Stream.emits(doc.fields.collect {
          case TextField(name, value) if name == SUGGEST_FIELD => value
        })
      )
      .compile
      .toList
      .unsafeRunSync()
    result
  }
}
