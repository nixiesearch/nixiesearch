package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class IndexMappingTest extends AnyFlatSpec with Matchers {
  it should "create mapping from document with string fields" in {
    val result = IndexMapping.fromDocument(List(Document(List(TextField("title", "yo")))), "test").unsafeRunSync()
    result shouldBe IndexMapping(
      name = "test",
      fields = List(
        TextFieldSchema("title", search = LexicalSearch(), sort = true, facet = true, filter = true)
      )
    )
  }
}
