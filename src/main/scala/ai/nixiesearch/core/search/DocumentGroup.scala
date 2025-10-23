package ai.nixiesearch.core.search

import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.field.TextField.FILTER_SUFFIX
import ai.nixiesearch.core.search.DocumentGroup.ROLE_FIELD
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{BinaryDocValuesField, Document, StoredField, StringField}
import org.apache.lucene.util.BytesRef

import scala.collection.mutable

case class DocumentGroup(
    id: String,
    parent: Document,
    children: mutable.ArrayBuffer[Document]
) {
  def toLuceneDocuments(): List[Document] = {
    parent.add(new BinaryDocValuesField("_id" + FILTER_SUFFIX, new BytesRef(id)))
    parent.add(new StoredField("_id", id))
    parent.add(new StringField("_id" + FILTER_SUFFIX, id, Store.NO))
    if (children.nonEmpty) {
      parent.add(new StringField(ROLE_FIELD, "parent", Store.YES))

      children.foreach(child => {
        child.add(new BinaryDocValuesField("_id" + FILTER_SUFFIX, new BytesRef(id)))
        child.add(new StoredField("_id", id))
        child.add(new StringField("_id" + FILTER_SUFFIX, id, Store.NO))
        child.add(new StringField(ROLE_FIELD, "child", Store.YES))
      })
    }
    children.toList :+ parent
  }
}

object DocumentGroup {
  val ROLE_FIELD   = "_role"
  val PARENT_FIELD = "_parent"

  def apply(id: String) = {
    val parent = new Document()
    new DocumentGroup(id, parent, mutable.ArrayBuffer())
  }

}
