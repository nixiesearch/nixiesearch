package ai.nixiesearch.core.codec

import org.apache.lucene.index.StoredFieldVisitor.Status
import org.apache.lucene.index.{FieldInfo, StoredFieldVisitor}

case class SuggestVisitor(field: String, var result: String = null, var score: Float = 0.0f)
    extends StoredFieldVisitor {
  override def needsField(fieldInfo: FieldInfo): StoredFieldVisitor.Status =
    if (fieldInfo.name == field) Status.YES else Status.NO

  override def stringField(fieldInfo: FieldInfo, value: String): Unit = {
    result = value
  }

  def getResult(): Option[String] = Option(result)
}
