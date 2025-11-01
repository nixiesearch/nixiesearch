package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName}
import ai.nixiesearch.core.{Document, Field}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.field.FieldCodec.WireDecodingError
import ai.nixiesearch.core.search.DocumentGroup
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.ByteBuffersDirectory
import org.scalatest.{Assertion, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StoredFieldTest extends AnyFlatSpec with Matchers with EitherValues {
  it should "decode text" in {
    roundtrip(TextField("name", "foo"), TextFieldSchema(name = StringName("name")))
  }

  it should "decode text[1]" in {
    roundtrip(TextListField("name", List("foo")), TextListFieldSchema(name = StringName("name")))
  }

  it should "decode text[2]" in {
    roundtrip(TextListField("name", List("foo", "bar")), TextListFieldSchema(name = StringName("name")))
  }

  it should "decode id" in {
    roundtrip(IdField("_id", "abc123"), IdFieldSchema(name = StringName("_id")))
  }

  it should "decode boolean true" in {
    roundtrip(BooleanField("flag", true), BooleanFieldSchema(name = StringName("flag")))
  }

  it should "decode boolean false" in {
    roundtrip(BooleanField("flag", false), BooleanFieldSchema(name = StringName("flag")))
  }

  it should "decode int" in {
    roundtrip(IntField("count", 42), IntFieldSchema(name = StringName("count")))
  }

  it should "decode int[]" in {
    roundtrip(IntListField("counts", List(1, 2, 3)), IntListFieldSchema(name = StringName("counts")))
  }

  it should "decode long" in {
    roundtrip(LongField("timestamp", 1234567890123L), LongFieldSchema(name = StringName("timestamp")))
  }

  it should "decode long[]" in {
    roundtrip(LongListField("timestamps", List(1L, 2L, 3L)), LongListFieldSchema(name = StringName("timestamps")))
  }

  it should "decode float" in {
    roundtrip(FloatField("score", 19.99f), FloatFieldSchema(name = StringName("score")))
  }

  it should "decode float[]" in {
    roundtrip(FloatListField("scores", List(1.5f, 2.5f)), FloatListFieldSchema(name = StringName("scores")))
  }

  it should "decode double" in {
    roundtrip(DoubleField("price", 19.99), DoubleFieldSchema(name = StringName("price")))
  }

  it should "decode double[]" in {
    roundtrip(DoubleListField("prices", List(1.5, 2.5)), DoubleListFieldSchema(name = StringName("prices")))
  }

  it should "decode date" in {
    roundtrip(DateField("date", 20089), DateFieldSchema(name = StringName("date")))
  }

  it should "decode datetime" in {
    roundtrip(DateTimeField("datetime", 1000), DateTimeFieldSchema(name = StringName("datetime")))
  }

  it should "decode geopoint" in {
    roundtrip(GeopointField("point", 40.7128, -74.0060), GeopointFieldSchema(name = StringName("point")))
  }

  it should "decode wildcart fields" in {
    val doc = writeread(
      fields = List(TextField("title_a", "foo"), TextField("title_b", "bar")),
      schema = TextFieldSchema(name = WildcardName("title_*", "title_", "")),
      fieldNames = List(WildcardName("title_*", "title_", ""))
    )
    doc.value.fields.map(_.name) shouldBe List("title_a", "title_b")
  }

  def roundtrip[F <: Field, S <: FieldSchema[F]](field: F, schema: S): Assertion = {
    val doc = writeread(List(field), schema, List(schema.name))
    doc.value.fields should contain(field)
  }

  def writeread[F <: Field, S <: FieldSchema[F]](
      fields: List[F],
      schema: S,
      fieldNames: List[FieldName]
  ): Either[WireDecodingError, Document] = {
    val dir    = new ByteBuffersDirectory()
    val writer = new IndexWriter(dir, new IndexWriterConfig())
    val buffer = DocumentGroup("id")
    fields.foreach(field => schema.codec.writeLucene(field, buffer))
    writer.addDocument(buffer.parent)
    writer.close()
    val reader  = DirectoryReader.open(dir)
    val visitor = DocumentVisitor(
      mapping = IndexMapping(
        name = IndexName("test"),
        fields = Map(schema.name -> schema),
        store = LocalStoreConfig(local = MemoryLocation())
      ),
      fields = fieldNames
    )
    reader.storedFields().document(0, visitor)
    visitor.asDocument(1.0)
  }
}
