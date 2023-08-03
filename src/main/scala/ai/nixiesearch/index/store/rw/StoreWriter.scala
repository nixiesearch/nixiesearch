package ai.nixiesearch.index.store.rw

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.codec.*
import ai.nixiesearch.index.store.LocalStore.DirectoryMapping
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.facet.FacetsConfig
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.document.{Document => LuceneDocument}
import java.util

case class StoreWriter(
    mapping: IndexMapping,
    writer: IndexWriter,
    directory: MMapDirectory,
    fc: FacetsConfig,
    analyzer: Analyzer
) extends Logging {

  def addDocuments(docs: List[Document]): Unit = {
    val all = new util.ArrayList[LuceneDocument]()
    docs.foreach(doc => {
      val buffer = new LuceneDocument()
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          mapping.textFields.get(name) match {
            case None          => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) => TextFieldWriter().write(field, mapping, buffer)
          }
        case field @ TextListField(name, value) =>
          mapping.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => TextListFieldWriter().write(field, mapping, buffer)
          }
        case field @ IntField(name, value) =>
          mapping.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => IntFieldWriter().write(field, mapping, buffer)
          }
        case field @ FloatField(name, value) =>
          mapping.floatFields.get(name) match {
            case None          => logger.warn(s"float field '$name' is not defined in mapping")
            case Some(mapping) => FloatFieldWriter().write(field, mapping, buffer)
          }
      }
      val finalized = fc.build(buffer)
      all.add(finalized)
    })
    writer.addDocuments(all)
  }

  def flush(): IO[Unit] = IO(writer.commit())

  def close(): IO[Unit] = info(s"closing index writer for index '${mapping.name}'") *> IO(writer.close())
}

object StoreWriter {
  def create(dm: DirectoryMapping): IO[StoreWriter] = IO {
    val config = new IndexWriterConfig(dm.analyzer)
    val writer = IndexWriter(dm.dir, config)
    val fc     = new FacetsConfig()
    StoreWriter(dm.mapping, writer, dm.dir, fc, dm.analyzer)
  }
}
