package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.core.Field

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.index.StoredFieldVisitor
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.StoredFieldVisitor.Status
import ai.nixiesearch.core.Logging
import ai.nixiesearch.config.FieldSchema.{
  BooleanFieldSchema,
  DateFieldSchema,
  DateTimeFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  GeopointFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextListFieldSchema
}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.*
import cats.effect.IO
import cats.syntax.all.*

case class DocumentVisitor(
    mapping: IndexMapping,
    fields: List[FieldName],
    collectedScalars: ArrayBuffer[Field] = ArrayBuffer.empty,
    collectedTextList: mutable.Map[String, ArrayBuffer[String]] = mutable.Map.empty,
    errors: ArrayBuffer[Exception] = ArrayBuffer.empty
) extends StoredFieldVisitor
    with Logging {

  def reset() = {
    collectedScalars.clear()
    collectedTextList.clear()
    errors.clear()
  }

  override def needsField(fieldInfo: FieldInfo): Status =
    if ((fieldInfo.name == "_id") || fields.exists(_.matches(fieldInfo.name))) Status.YES else Status.NO

  override def stringField(fieldInfo: FieldInfo, value: String): Unit = mapping.fieldSchema(fieldInfo.name) match {
    case None => logger.warn(s"field ${fieldInfo.name} is not found in mapping, but collected: this should not happen")
    case Some(spec: TextFieldSchema) =>
      TextField.readLucene(fieldInfo.name, spec, value) match {
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
    mapping.fieldSchema(fieldInfo.name) match {
      case Some(schema: IntFieldSchema) => collectField(Some(schema), fieldInfo.name, value, IntField)
      case Some(schema: BooleanFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, BooleanField)
      case Some(schema: DateFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, DateField)
      case Some(other) =>
        logger.warn(s"field ${fieldInfo.name} is int on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }
  }

  override def longField(fieldInfo: FieldInfo, value: Long): Unit = {
    mapping.fieldSchema(fieldInfo.name) match {
      case Some(schema: LongFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, LongField)
      case Some(schema: DateTimeFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, DateTimeField)
      case Some(other) =>
        logger.warn(s"field ${fieldInfo.name} is int on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }

  }

  override def floatField(fieldInfo: FieldInfo, value: Float): Unit =
    collectField(mapping.fieldSchemaOf[FloatFieldSchema](fieldInfo.name), fieldInfo.name, value, FloatField)

  override def doubleField(fieldInfo: FieldInfo, value: Double): Unit =
    collectField(mapping.fieldSchemaOf[DoubleFieldSchema](fieldInfo.name), fieldInfo.name, value, DoubleField)

  override def binaryField(fieldInfo: FieldInfo, value: Array[Byte]): Unit =
    collectField(mapping.fieldSchemaOf[GeopointFieldSchema](fieldInfo.name), fieldInfo.name, value, GeopointField)

  private def collectField[T, F <: Field, S <: FieldSchema[F]](
      specOption: Option[S],
      name: String,
      value: T,
      codec: FieldCodec[F, S, T]
  ): Unit = specOption match {
    case Some(spec) =>
      codec.readLucene(name, spec, value) match {
        case Left(error)  => errors.addOne(error)
        case Right(value) => collectedScalars.addOne(value)
      }
    case None => logger.warn(s"field $name is not found in mapping, but visited: this should not happen")
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

object DocumentVisitor {
  def create(mapping: IndexMapping, fields: List[FieldName]): IO[DocumentVisitor] = {
    fields
      .traverse(field =>
        mapping.fieldSchema(field.name) match {
          case None => IO.raiseError(UserError(s"field ${field.name} is not defined in mapping"))
          case Some(mapping) if !mapping.store => IO.raiseError(UserError(s"field ${field.name} is not stored"))
          case Some(other)                     => IO.pure(other)
        }
      )
      .map(_ => new DocumentVisitor(mapping, fields))
  }
}
