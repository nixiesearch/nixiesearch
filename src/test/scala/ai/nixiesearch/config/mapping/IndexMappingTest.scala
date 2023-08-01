package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.SearchType.LexicalSearch
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import scala.util.Try

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

  it should "migrate compatible fields: ints" in {
    val before = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val result = before.migrate(after).unsafeRunSync()
    result shouldBe after
  }

  it should "fail on incompatible migrations" in {
    val before = IndexMapping("foo", fields = Map("test" -> IntFieldSchema("test")))
    val after  = IndexMapping("foo", fields = Map("test" -> TextFieldSchema("test")))
    val result = Try(before.migrate(after).unsafeRunSync())
    result.isFailure shouldBe true
  }
}
