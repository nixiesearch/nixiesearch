package ai.nixiesearch.index

import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.index.IndexWriter
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import java.util.ArrayList
import org.apache.lucene.index.IndexableField
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.Field.TextListField
import ai.nixiesearch.core.Field.IntField
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.codec.TextFieldWriter
import ai.nixiesearch.core.codec.TextListFieldWriter
import ai.nixiesearch.core.codec.IntFieldWriter
import java.nio.file.Path
import org.apache.lucene.index.IndexWriterConfig

case class IndexBuilder(dir: MMapDirectory, writer: IndexWriter, schema: IndexMapping) extends Logging {
  def addDocuments(docs: List[Document]): Unit = {
    val all = new ArrayList[ArrayList[IndexableField]]()
    docs.foreach(doc => {
      val buffer = new ArrayList[IndexableField](8)
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          schema.textFields.get(name) match {
            case None          => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) => TextFieldWriter().write(field, mapping, buffer)
          }
        case field @ TextListField(name, value) =>
          schema.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => TextListFieldWriter().write(field, mapping, buffer)
          }
        case field @ IntField(name, value) =>
          schema.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => IntFieldWriter().write(field, mapping, buffer)
          }
      }
      all.add(buffer)
    })
    writer.addDocuments(all)
  }
}

object IndexBuilder {
  def open(path: Path, mapping: IndexMapping) = {
    val dir    = new MMapDirectory(path)
    val config = new IndexWriterConfig()
    val writer = new IndexWriter(dir, config)
    new IndexBuilder(dir, writer, mapping)
  }
}
