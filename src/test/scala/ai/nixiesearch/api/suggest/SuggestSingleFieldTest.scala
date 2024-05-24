package ai.nixiesearch.api.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.Language.Generic
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestSchema}
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, NoSearch}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.util.SearchTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.apache.lucene.codecs.{FilterCodec, PostingsFormat}
import org.apache.lucene.codecs.lucene99.Lucene99Codec
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.suggest.document.{Completion99PostingsFormat, CompletionPostingsFormat, PrefixCompletionQuery, SuggestField, SuggestIndexSearcher}
import org.apache.lucene.store.ByteBuffersDirectory
import org.checkerframework.checker.units.qual.Prefix

class SuggestSingleFieldTest extends SearchTest with Matchers {
  val mapping = IndexMapping(
    name = "test",
    fields = List(
      TextFieldSchema(name = "_id", filter = true),
      TextFieldSchema(name = "title", search = NoSearch, suggest = Some(SuggestSchema()))
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  val docs = List(
    Document(List(TextField("title", "hello world"))),
    Document(List(TextField("title", "I like hotdogs"))),
    Document(List(TextField("title", "where is my mind?")))
  )

  it should "generate suggestions" in withIndex { nixie =>
    {
      val resp = nixie.searcher.suggest(SuggestRequest(query = "he", fields = List("title"))).unsafeRunSync()
      resp.suggestions.map(_.text) shouldBe List("hello", "hello world")
    }
  }
}
