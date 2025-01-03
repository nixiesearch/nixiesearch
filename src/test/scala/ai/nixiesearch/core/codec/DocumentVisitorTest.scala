package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.MatchAllQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.FieldSchema.{
  BooleanFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  GeopointFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextListFieldSchema
}
import ai.nixiesearch.core.Document
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.SearchTest

class DocumentVisitorTest extends AnyFlatSpec with Matchers with SearchTest {
  val docs = Nil
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema("_id"),
      TextFieldSchema("title"),
      TextListFieldSchema("title2"),
      IntFieldSchema("count"),
      LongFieldSchema("long"),
      FloatFieldSchema("float"),
      DoubleFieldSchema("double"),
      BooleanFieldSchema("boolean"),
      GeopointFieldSchema("geo")
    ),
    store = LocalStoreConfig(MemoryLocation())
  )

  it should "collect doc from fields" in withIndex { store =>
    {
      val source =
        Document(
          List(
            TextField("_id", "1"),
            TextField("title", "foo"),
            TextListField("title2", List("foo", "bar")),
            IntField("count", 1),
            LongField("long", 1),
            FloatField("float", 1),
            DoubleField("double", 1),
            BooleanField("boolean", true),
            GeopointField("geo", 1, 2)
          )
        )
      store.indexer.addDocuments(List(source)).unsafeRunSync()
      store.indexer.flush().unsafeRunSync()
      store.indexer.index.sync().unsafeRunSync()
      store.searcher.sync().unsafeRunSync()

      val request = SearchRequest(
        MatchAllQuery(),
        fields = List("_id", "title", "title2", "count", "long", "float", "double", "boolean", "geo")
      )
      val docs           = store.searcher.search(request).unsafeRunSync()
      val actualFields   = docs.hits.head.fields.sortBy(_.name)
      val expectedFields = (source.fields :+ FloatField("_score", 1.0)).sortBy(_.name)
      actualFields shouldBe expectedFields
    }
  }
}
