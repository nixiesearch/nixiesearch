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
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.FieldName.WildcardName
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.{
  BinaryStoredField,
  DoubleStoredField,
  FloatStoredField,
  IntStoredField,
  LongStoredField,
  StringStoredField
}
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.search.DocumentGroup
import cats.effect.IO
import cats.syntax.all.*

case class DocumentVisitor(
    mapping: IndexMapping,
    fields: List[FieldName],
    collected: ArrayBuffer[StoredLuceneField] = ArrayBuffer.empty,
    errors: ArrayBuffer[Exception] = ArrayBuffer.empty
) extends StoredFieldVisitor
    with Logging {
  val stringFields: Set[String]          = fields.collect { case FieldName.StringName(name) => name }.toSet
  val wildcardFields: List[WildcardName] = fields.collect { case w: WildcardName => w }

  def reset() = {
    collected.clear()
    errors.clear()
  }

  override def needsField(fieldInfo: FieldInfo): Status = {
    val name = fieldInfo.name
    if ((name == "_id") || stringFields.contains(name) || wildcardFields.exists(_.matches(name))) {
      Status.YES
    } else {
      Status.NO
    }
  }

  override def stringField(fieldInfo: FieldInfo, value: String): Unit = {
    collected.addOne(StringStoredField(fieldInfo.name, value))
  }

  override def intField(fieldInfo: FieldInfo, value: Int): Unit =
    collected.addOne(IntStoredField(fieldInfo.name, value))

  override def longField(fieldInfo: FieldInfo, value: Long): Unit =
    collected.addOne(LongStoredField(fieldInfo.name, value))

  override def floatField(fieldInfo: FieldInfo, value: Float): Unit =
    collected.addOne(FloatStoredField(fieldInfo.name, value))

  override def doubleField(fieldInfo: FieldInfo, value: Double): Unit =
    collected.addOne(DoubleStoredField(fieldInfo.name, value))

  override def binaryField(fieldInfo: FieldInfo, value: Array[Byte]): Unit =
    collected.addOne(BinaryStoredField(fieldInfo.name, value))

  def asDocument(score: Float): Document = {
    val fields = Nil
    Document(fields)
  }
}

object DocumentVisitor {
  case class StoredDocument(fields: Iterable[StoredLuceneField])
  sealed trait StoredLuceneField {
    def name: String
  }
  object StoredLuceneField {
    case class StringStoredField(name: String, value: String)      extends StoredLuceneField
    case class IntStoredField(name: String, value: Int)            extends StoredLuceneField
    case class LongStoredField(name: String, value: Long)          extends StoredLuceneField
    case class FloatStoredField(name: String, value: Float)        extends StoredLuceneField
    case class DoubleStoredField(name: String, value: Double)      extends StoredLuceneField
    case class BinaryStoredField(name: String, value: Array[Byte]) extends StoredLuceneField
  }

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
