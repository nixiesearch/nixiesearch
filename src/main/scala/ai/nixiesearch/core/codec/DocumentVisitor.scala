package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.index.StoredFieldVisitor
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.StoredFieldVisitor.Status
import ai.nixiesearch.core.Logging
import ai.nixiesearch.config.FieldSchema.{BooleanFieldSchema, IntFieldSchema, TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*

case class DocumentVisitor(
    mapping: IndexMapping,
    fields: Set[String],
    collectedScalars: ArrayBuffer[Field] = ArrayBuffer.empty,
    collectedTextList: mutable.Map[String, ArrayBuffer[String]] = mutable.Map.empty,
    errors: ArrayBuffer[Exception] = ArrayBuffer.empty
) extends StoredFieldVisitor
    with Logging {
  override def needsField(fieldInfo: FieldInfo): Status =
    if ((fieldInfo.name == "_id") || fields.contains(fieldInfo.name)) Status.YES else Status.NO

  override def stringField(fieldInfo: FieldInfo, value: String): Unit = mapping.fields.get(fieldInfo.name) match {
    case None => logger.warn(s"field ${fieldInfo.name} is not found in mapping, but collected: this should not happen")
    case Some(spec: TextFieldSchema) =>
      TextField.readLucene(spec, value) match {
        case Left(err)    => errors.addOne(err)
        case Right(field) => collectedScalars.addOne(field)
      }
    case Some(_: TextListFieldSchema) =>
      collectedTextList.get(fieldInfo.name) match {
        case None =>
          val buf = new ArrayBuffer[String](4)
          buf.addOne(value)
          collectedTextList.addOne(fieldInfo.name -> buf)
        case Some(buf) =>
          buf.addOne(value)
      }
    case Some(other) =>
      logger.warn(s"field ${fieldInfo.name} is defined as $other, and cannot accept string value '$value'")
  }

  override def intField(fieldInfo: FieldInfo, value: Int): Unit = {
    mapping.fields.get(fieldInfo.name) match {
      case Some(int: IntFieldSchema) => collectField(mapping.intFields, fieldInfo.name, value, IntField)
      case Some(bool: BooleanFieldSchema) =>
        collectField(mapping.booleanFields, fieldInfo.name, value, BooleanField)
      case Some(other) =>
        logger.warn(s"field ${fieldInfo.name} is int on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }
  }

  override def longField(fieldInfo: FieldInfo, value: Long): Unit =
    collectField(mapping.longFields, fieldInfo.name, value, LongField)

  override def floatField(fieldInfo: FieldInfo, value: Float): Unit =
    collectField(mapping.floatFields, fieldInfo.name, value, FloatField)

  override def doubleField(fieldInfo: FieldInfo, value: Double): Unit =
    collectField(mapping.doubleFields, fieldInfo.name, value, DoubleField)

  override def binaryField(fieldInfo: FieldInfo, value: Array[Byte]): Unit =
    collectField(mapping.geopointFields, fieldInfo.name, value, GeopointField)

  private def collectField[T, F <: Field, S <: FieldSchema[F]](
      specs: Map[String, S],
      name: String,
      value: T,
      codec: FieldCodec[F, S, T]
  ): Unit = specs.get(name) match {
    case Some(spec) =>
      codec.readLucene(spec, value) match {
        case Left(error)  => errors.addOne(error)
        case Right(value) => collectedScalars.addOne(value)
      }
    case None => logger.warn("field ${fieldInfo.name} is not found in mapping, but visited: this should not happen")
  }

  def asDocument(score: Float): Document = {
    val fields = List.concat(
      collectedScalars.toList,
      collectedTextList.map { case (name, values) => TextListField(name, values.toList) },
      List(FloatField("_score", score))
    )
    Document(fields)
  }
}
