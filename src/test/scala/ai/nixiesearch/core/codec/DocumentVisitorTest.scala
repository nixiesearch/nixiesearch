package ai.nixiesearch.core.codec

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.index.IndexBuilder
import java.nio.file.Paths
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.core.Document
import ai.nixiesearch.index.Index
import java.nio.file.Path
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import java.nio.file.Files
import cats.effect.unsafe.implicits.global
import org.apache.lucene.search.MatchAllDocsQuery
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.Field.IntField

class DocumentVisitorTest extends AnyFlatSpec with Matchers {
  it should "collect doc from fields" in {
    val source = Document(List(TextField("id", "1"), TextField("title", "foo"), IntField("count", 1)))
    val tmp    = Files.createTempDirectory("test")
    val mapping = IndexMapping(
      name = "test",
      fields = List(TextFieldSchema("id"), TextFieldSchema("title"), IntFieldSchema("count"))
    )
    val writer = IndexBuilder.open(tmp, mapping)
    writer.addDocuments(List(source))
    writer.writer.commit()
    val index   = Index.read(writer.dir).unsafeRunSync()
    val docs    = index.searcher.search(new MatchAllDocsQuery(), 10)
    val visitor = DocumentVisitor(mapping, Set("id", "title", "count"))
    val read    = index.reader.storedFields().document(docs.scoreDocs(0).doc, visitor)
    val out     = visitor.asDocument()
    out shouldBe source
  }
}
