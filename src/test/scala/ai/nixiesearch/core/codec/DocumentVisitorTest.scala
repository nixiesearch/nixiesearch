package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.core.Document
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import cats.effect.unsafe.implicits.global
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.util.SearchTest
import ai.nixiesearch.config.mapping.FieldName.StringName

import scala.util.{Success, Try}

class DocumentVisitorTest extends AnyFlatSpec with Matchers with SearchTest {
  val docs    = Nil
  val mapping = IndexMapping(
    name = IndexName.unsafe("test"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true, facet = true),
      TextFieldSchema(StringName("title")),
      TextFieldSchema(StringName("title_nonstore"), store = false),
      TextListFieldSchema(StringName("title2")),
      TextFieldSchema(FieldName.parse("str_*").toOption.get),
      IntFieldSchema(StringName("int")),
      LongFieldSchema(StringName("long")),
      FloatFieldSchema(StringName("float")),
      DoubleFieldSchema(StringName("double")),
      IntListFieldSchema(StringName("intlist")),
      LongListFieldSchema(StringName("longlist")),
      FloatListFieldSchema(StringName("floatlist")),
      DoubleListFieldSchema(StringName("doublelist")),
      BooleanFieldSchema(StringName("boolean")),
      GeopointFieldSchema(StringName("geo")),
      DateFieldSchema(StringName("date")),
      DateTimeFieldSchema(StringName("datetime"))
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
            TextField("str_foo", "foo"),
            TextField("str_bar", "bar"),
            IntField("int", 1),
            LongField("long", 1),
            FloatField("float", 1),
            DoubleField("double", 1),
            IntListField("intlist", List(1, 1)),
            LongListField("longlist", List(1, 1)),
            FloatListField("floatlist", List(1, 1)),
            DoubleListField("doublelist", List(1, 1)),
            BooleanField("boolean", true),
            GeopointField("geo", 1, 2),
            DateField("date", 1),
            DateTimeField("datetime", 1)
          )
        )
      store.indexer.addDocuments(List(source)).unsafeRunSync()
      store.indexer.flush().unsafeRunSync()
      store.indexer.index.sync().unsafeRunSync()
      store.searcher.sync().unsafeRunSync()

      val request = SearchRequest(
        MatchAllQuery(),
        fields = List(
          StringName("_id"),
          StringName("title"),
          StringName("title2"),
          FieldName.parse("str_*").toOption.get,
          StringName("int"),
          StringName("long"),
          StringName("float"),
          StringName("double"),
          StringName("intlist"),
          StringName("longlist"),
          StringName("floatlist"),
          StringName("doublelist"),
          StringName("boolean"),
          StringName("geo"),
          StringName("date"),
          StringName("datetime")
        )
      )
      val docs           = store.searcher.search(request).unsafeRunSync()
      val actualFields   = docs.hits.head.fields.sortBy(_.name)
      val expectedFields = (source.fields :+ FloatField("_score", 1.0)).sortBy(_.name)
      actualFields should contain theSameElementsAs expectedFields
    }
  }
  // should fail
  it should "fail on nonstore fields" in withIndex { store =>
    {
      val source =
        Document(
          List(
            TextField("_id", "1"),
            TextField("title_nonstore", "foo")
          )
        )
      store.indexer.addDocuments(List(source)).unsafeRunSync()
      store.indexer.flush().unsafeRunSync()
      store.indexer.index.sync().unsafeRunSync()
      store.searcher.sync().unsafeRunSync()

      val request = SearchRequest(
        MatchAllQuery(),
        fields = List(
          StringName("_id"),
          StringName("title_nonstore")
        )
      )
      val docs = Try(store.searcher.search(request).unsafeRunSync())
      docs.isFailure shouldBe true
    }
  }

  it should "handle fast-path fetches" in withIndex { store =>
    val source = List(
      Document(List(TextField("_id", "1"))),
      Document(List(TextField("_id", "2"))),
      Document(List(TextField("_id", "3"))),
      Document(List(TextField("_id", "4"))),
      Document(List(TextField("_id", "5")))
    )
    store.indexer.addDocuments(source).unsafeRunSync()
    store.indexer.flush().unsafeRunSync()
    store.indexer.index.sync().unsafeRunSync()
    store.searcher.sync().unsafeRunSync()

    val request = SearchRequest(
      MatchAllQuery(),
      fields = List(StringName("_id"))
    )
    val docs = Try(store.searcher.search(request).unsafeRunSync())
    val ids  = docs.map(_.hits.flatMap(_.fields.collect { case TextField("_id", value, _) => value }))
    ids shouldBe Success(List("1", "2", "3", "4", "5"))
  }
}
