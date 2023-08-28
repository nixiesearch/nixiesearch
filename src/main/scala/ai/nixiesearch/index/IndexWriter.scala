package ai.nixiesearch.index

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.codec.{FloatFieldWriter, IntFieldWriter, TextFieldWriter, TextListFieldWriter}
import ai.nixiesearch.core.nn.model.BiEncoderCache
import cats.effect.{IO, Ref}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.IndexWriter as LuceneIndexWriter
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.document.Document as LuceneDocument

import java.util

trait IndexWriter extends Logging {
  def name: String
  def config: StoreConfig
  def mappingRef: Ref[IO, Option[IndexMapping]]
  def refreshMapping(mapping: IndexMapping): IO[Unit]
  def writer: LuceneIndexWriter
  def directory: MMapDirectory
  def analyzer: Analyzer
  def encoders: BiEncoderCache

  lazy val textFieldWriter     = TextFieldWriter(encoders)
  lazy val textListFieldWriter = TextListFieldWriter()
  lazy val intFieldWriter      = IntFieldWriter()
  lazy val floatFieldWriter    = FloatFieldWriter()

  def mapping(): IO[IndexMapping] = mappingRef.get.flatMap {
    case Some(value) => IO.pure(value)
    case None        => IO.raiseError(new Exception("this should never happen"))
  }

  def addDocuments(docs: List[Document]): IO[Unit] = for {
    mapping <- mapping()
  } yield {
    val all = new util.ArrayList[LuceneDocument]()
    docs.foreach(doc => {
      val buffer = new LuceneDocument()
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          mapping.textFields.get(name) match {
            case None          => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) => textFieldWriter.write(field, mapping, buffer)
          }
        case field @ TextListField(name, value) =>
          mapping.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => textListFieldWriter.write(field, mapping, buffer)
          }
        case field @ IntField(name, value) =>
          mapping.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => intFieldWriter.write(field, mapping, buffer)
          }
        case field @ FloatField(name, value) =>
          mapping.floatFields.get(name) match {
            case None          => logger.warn(s"float field '$name' is not defined in mapping")
            case Some(mapping) => floatFieldWriter.write(field, mapping, buffer)
          }
      }
      all.add(buffer)
    })
    writer.addDocuments(all)
    logger.info(s"wrote ${all.size()} docs")
  }

  def flush(): IO[Unit] = IO(writer.commit())

  def close(): IO[Unit] = for {
    m <- mapping()
    _ <- info(s"closing index writer for index '${m.name}'")
    _ <- IO(writer.close())
  } yield {}

}
