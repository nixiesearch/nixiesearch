package ai.nixiesearch.core.codec

import ai.nixiesearch.config
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.core.Field

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.index.StoredFieldVisitor
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.StoredFieldVisitor.Status
import ai.nixiesearch.core.Logging
import ai.nixiesearch.config.FieldSchema.{BooleanFieldSchema, DateFieldSchema, DateTimeFieldSchema, DoubleFieldSchema, DoubleListFieldSchema, FloatFieldSchema, FloatListFieldSchema, GeopointFieldSchema, IntFieldSchema, IntListFieldSchema, LongFieldSchema, LongListFieldSchema, TextFieldSchema, TextListFieldSchema}
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
    collectedIntList: mutable.Map[String, ArrayBuffer[Int]] = mutable.Map.empty,
    collectedLongList: mutable.Map[String, ArrayBuffer[Long]] = mutable.Map.empty,
    collectedFloatList: mutable.Map[String, ArrayBuffer[Float]] = mutable.Map.empty,
    collectedDoubleList: mutable.Map[String, ArrayBuffer[Double]] = mutable.Map.empty,
    errors: ArrayBuffer[Exception] = ArrayBuffer.empty
) extends StoredFieldVisitor
    with Logging {

  def reset() = {
    collectedScalars.clear()
    collectedTextList.clear()
    collectedIntList.clear()
    collectedLongList.clear()
    collectedFloatList.clear()
    collectedDoubleList.clear()
    errors.clear()
  }

  def collectListField[F <: Field, T](fi: FieldInfo, value: T, store: mutable.Map[String, ArrayBuffer[T]]) =
    store.get(fi.name) match {
      case None =>
        val buf = new ArrayBuffer[T](4)
        buf.addOne(value)
        store.addOne(fi.name -> buf)
      case Some(buf) =>
        buf.addOne(value)
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
      collectListField(fieldInfo, value, collectedTextList)

    case Some(other) =>
      logger.warn(s"field ${fieldInfo.name} is defined as $other, and cannot accept string value '$value'")
  }

  override def intField(fieldInfo: FieldInfo, value: Int): Unit = {
    mapping.fieldSchema(fieldInfo.name) match {
      case Some(schema: IntFieldSchema)     => collectField(Some(schema), fieldInfo.name, value, IntField)
      case Some(schema: BooleanFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, BooleanField)
      case Some(schema: DateFieldSchema) =>
        collectField(Some(schema), fieldInfo.name, value, DateField)
      case Some(schema: IntListFieldSchema) =>
        collectListField(fieldInfo, value, collectedIntList)
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
      case Some(schema: LongListFieldSchema) =>
        collectListField(fieldInfo, value, collectedLongList)
      case Some(other) =>
        logger.warn(s"field ${fieldInfo.name} is int on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }

  }

  override def floatField(fieldInfo: FieldInfo, value: Float): Unit = {
    mapping.fieldSchema(fieldInfo.name) match {
      case Some(schema: FloatFieldSchema)     => collectField(Some(schema), fieldInfo.name, value, FloatField)
      case Some(schema: FloatListFieldSchema) => collectListField(fieldInfo, value, collectedFloatList)
      case Some(other)                        =>
        logger.warn(s"field ${fieldInfo.name} is float on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }

  }

  override def doubleField(fieldInfo: FieldInfo, value: Double): Unit =
    mapping.fieldSchema(fieldInfo.name) match {
      case Some(schema: DoubleFieldSchema)     => collectField(Some(schema), fieldInfo.name, value, DoubleField)
      case Some(schema: DoubleListFieldSchema) => collectListField(fieldInfo, value, collectedDoubleList)
      case Some(other)                         =>
        logger.warn(s"field ${fieldInfo.name} is double on disk, but ${other} in the mapping")
      case None =>
        logger.warn(s"field ${fieldInfo.name} is not defined in mapping")
    }

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
      collectedIntList.map { case (name, values) => IntListField(name, values.toList) },
      collectedLongList.map { case (name, values) => LongListField(name, values.toList) },
      collectedFloatList.map { case (name, values) => FloatListField(name, values.toList) },
      collectedDoubleList.map { case (name, values) => DoubleListField(name, values.toList) },
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
