package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Field.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*
import io.circe.parser.*

class DocumentJsonEncoderTest extends AnyFlatSpec with Matchers {
//  val mapping = IndexMapping(
//    name = IndexName("test"),
//    fields = List(
//      TextFieldSchema(StringName("_id"), filter = true),
//      TextFieldSchema(StringName("text")),
//      TextListFieldSchema(StringName("textlist")),
//      IntFieldSchema(StringName("int")),
//      LongFieldSchema(StringName("long")),
//      DoubleFieldSchema(StringName("double")),
//      FloatFieldSchema(StringName("float")),
//      IntListFieldSchema(StringName("intlist")),
//      LongListFieldSchema(StringName("longlist")),
//      DoubleListFieldSchema(StringName("doublelist")),
//      FloatListFieldSchema(StringName("floatlist"))
//    ),
//    store = LocalStoreConfig(MemoryLocation())
//  )
//  val doc = Document(
//    List(
//      TextField("_id", "1"),
//      TextField("text", "test"),
//      TextListField("textlist", List("test")),
//      IntField("int", 1),
//      LongField("long", 1L),
//      DoubleField("double", 1.0),
//      FloatField("float", 1.0f),
//      IntListField("intlist", List(1)),
//      LongListField("longlist", List(1L)),
//      DoubleListField("doublelist", List(1.0)),
//      FloatListField("floatlist", List(1.0f))
//    )
//  )
//
//  it should "encode/decode doc to json" in {
//    val codec   = Document.codecFor(mapping)
//    val json    = doc.asJson(using codec).noSpaces
//    val decoded = decode[Document](json)(using codec)
//    decoded shouldBe Right(doc)
//  }
}
