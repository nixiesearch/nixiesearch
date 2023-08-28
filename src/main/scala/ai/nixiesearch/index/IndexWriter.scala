package ai.nixiesearch.index

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.codec.{FloatFieldWriter, IntFieldWriter, TextFieldWriter, TextListFieldWriter}
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.rw.{StoreReader, StoreWriter}
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.IndexWriter as LuceneIndexWriter
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.document.Document as LuceneDocument

import java.util

trait IndexWriter extends Logging {
  def config: StoreConfig
  def mapping: IO[IndexMapping]
  def refreshMapping(mapping: IndexMapping): IO[Unit]
  def writer: LuceneIndexWriter
  def directory: MMapDirectory
  def analyzer: Analyzer
  def encoders: BiEncoderCache

  lazy val textFieldWriter     = TextFieldWriter(encoders)
  lazy val textListFieldWriter = TextListFieldWriter()
  lazy val intFieldWriter      = IntFieldWriter()
  lazy val floatFieldWriter    = FloatFieldWriter()

  def addDocuments(docs: List[Document]): IO[Unit] = for {
    m <- mapping
  } yield {
    val all = new util.ArrayList[LuceneDocument]()
    docs.foreach(doc => {
      val buffer = new LuceneDocument()
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          m.textFields.get(name) match {
            case None          => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) => textFieldWriter.write(field, mapping, buffer)
          }
        case field @ TextListField(name, value) =>
          m.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => textListFieldWriter.write(field, mapping, buffer)
          }
        case field @ IntField(name, value) =>
          m.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => intFieldWriter.write(field, mapping, buffer)
          }
        case field @ FloatField(name, value) =>
          m.floatFields.get(name) match {
            case None          => logger.warn(s"float field '$name' is not defined in mapping")
            case Some(mapping) => floatFieldWriter.write(field, mapping, buffer)
          }
      }
      all.add(buffer)
    })
    writer.addDocuments(all)
  }

  def flush(): IO[Unit] = IO(writer.commit())

  def close(): IO[Unit] = for {
    m <- mapping
    _ <- info(s"closing index writer for index '${m.name}'")
    _ <- IO(writer.close())
  } yield {}

}
