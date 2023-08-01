package ai.nixiesearch.index.store

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.SearchType.LexicalSearch
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.codec.*
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.IndexSnapshot
import ai.nixiesearch.index.store.LocalStore.DirectoryMapping
import ai.nixiesearch.index.store.Store.{StoreReader, StoreWriter}
import cats.effect.IO
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig, IndexableField}
import org.apache.lucene.search.{IndexSearcher, Query}
import org.apache.lucene.store.Directory

import java.util.ArrayList
import scala.jdk.CollectionConverters.*

trait Store() {
  def config: StoreConfig
  def reader(index: IndexMapping): IO[Option[StoreReader]]
  def writer(index: IndexMapping): IO[StoreWriter]
}

object Store extends Logging {
  case class StoreReader(mapping: IndexMapping, reader: IndexReader, dir: Directory, searcher: IndexSearcher) {
    def search(query: Query, fields: List[String], n: Int): IO[List[Document]] = IO {
      val top      = searcher.search(query, n)
      val fieldSet = fields.toSet
      val docs = top.scoreDocs.map(doc => {
        val visitor = DocumentVisitor(mapping, fieldSet)
        reader.storedFields().document(doc.doc, visitor)
        visitor.asDocument()
      })
      docs.toList
    }
    def close(): IO[Unit] = info(s"closing index reader for index '${mapping.name}'") *> IO(reader.close())
  }

  object StoreReader {
    def create(dm: DirectoryMapping): IO[StoreReader] = IO {
      val reader = DirectoryReader.open(dm.dir)
      StoreReader(dm.mapping, reader, dm.dir, new IndexSearcher(reader))
    }
  }
  case class StoreWriter(mapping: IndexMapping, writer: IndexWriter) {
    def addDocuments(docs: List[Document]): Unit = {
      val all = new ArrayList[ArrayList[IndexableField]]()
      docs.foreach(doc => {
        val buffer = new ArrayList[IndexableField](8)
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
        }
        all.add(buffer)
      })
      writer.addDocuments(all)
    }

    def close(): IO[Unit] = info(s"closing index writer for index '${mapping.name}'") *> IO(writer.close())
  }

  object StoreWriter {
    def create(dm: DirectoryMapping): IO[StoreWriter] = IO {
      val fieldAnalyzers = dm.mapping.fields.values.collect {
        case TextFieldSchema(name, LexicalSearch(language), _, _, _, _)     => name -> language.analyzer
        case TextListFieldSchema(name, LexicalSearch(language), _, _, _, _) => name -> language.analyzer
      }
      val analyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldAnalyzers.toMap.asJava)
      val config   = new IndexWriterConfig()
      val writer   = IndexWriter(dm.dir, config)
      StoreWriter(dm.mapping, writer)
    }
  }
}
