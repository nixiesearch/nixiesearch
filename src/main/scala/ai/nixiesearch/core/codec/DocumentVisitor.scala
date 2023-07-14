package ai.nixiesearch.core.codec

import ai.nixiesearch.config.IndexMapping
import ai.nixiesearch.core.Field
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import ai.nixiesearch.core.codec.DocumentVisitor.DocumentFields
import org.apache.lucene.index.StoredFieldVisitor
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.StoredFieldVisitor.Status
import ai.nixiesearch.core.Logging
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import io.circe.Json
import io.circe.JsonObject
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*

case class DocumentVisitor(mapping: IndexMapping, fields: Set[String], doc: DocumentFields = DocumentFields())
    extends StoredFieldVisitor
    with Logging {
  override def needsField(fieldInfo: FieldInfo): Status = if (fields.contains(fieldInfo.name)) Status.YES else Status.NO

  override def stringField(fieldInfo: FieldInfo, value: String): Unit = mapping.fields.get(fieldInfo.name) match {
    case None => logger.warn(s"field ${fieldInfo.name} is not found in mapping, but collected: this should not happen")
    case Some(_: TextFieldSchema) => doc.text.addOne(fieldInfo.name -> value)
    case Some(_: TextListFieldSchema) =>
      doc.textList.get(fieldInfo.name) match {
        case None =>
          val buf = new ArrayBuffer[String](4)
          buf.addOne(value)
          doc.textList.addOne(fieldInfo.name -> buf)
        case Some(buf) =>
          buf.addOne(value)
      }
    case Some(other) =>
      logger.warn(s"field ${fieldInfo.name} is defined as $other, and cannot accept string value '$value'")
  }

  override def intField(fieldInfo: FieldInfo, value: Int): Unit = mapping.fields.get(fieldInfo.name) match {
    case None => logger.warn(s"field ${fieldInfo.name} is not found in mapping, but collected: this should not happen")
    case Some(_: IntFieldSchema) => doc.int.addOne(fieldInfo.name -> value)
    case Some(other) =>
      logger.warn(s"field ${fieldInfo.name} is defined as $other, and cannot accept int value '$value'")
  }

  def asJson(): Json = {
    val fields = new ArrayBuffer[(String, Json)](4)
    doc.int.foreach(f => fields.addOne(f._1 -> Json.fromInt(f._2)))
    doc.text.foreach(f => fields.addOne(f._1 -> Json.fromString(f._2)))
    doc.textList.foreach(f => fields.addOne(f._1 -> Json.fromValues(f._2.map(Json.fromString))))
    Json.fromJsonObject(JsonObject.fromIterable(fields))
  }

  def asDocument(): Document = {
    val fields = List.concat(
      doc.text.map(f => TextField(f._1, f._2)),
      doc.textList.map(f => TextListField(f._1, f._2.toList)),
      doc.int.map(f => IntField(f._1, f._2))
    )
    Document(fields)
  }
}

object DocumentVisitor {
  case class DocumentFields(
      text: mutable.Map[String, String] = mutable.Map.empty,
      textList: mutable.Map[String, ArrayBuffer[String]] = mutable.Map.empty,
      int: mutable.Map[String, Int] = mutable.Map.empty
  )

}
