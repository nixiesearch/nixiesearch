package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.nn.model.OnnxBiEncoder
import ai.nixiesearch.core.{Field, Logging}
import org.apache.lucene.index.{IndexWriter, IndexableField}
import org.apache.lucene.document.Document as LuceneDocument

trait FieldWriter[T <: Field, S <: FieldSchema[T]] extends Logging {
  def write(field: T, spec: S, buffer: LuceneDocument, embeddings: Map[String, Array[Float]]): Unit
}
