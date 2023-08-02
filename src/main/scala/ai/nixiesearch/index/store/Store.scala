package ai.nixiesearch.index.store

import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.codec.*
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.store.LocalStore.DirectoryMapping
import ai.nixiesearch.index.store.Store.{StoreReader, StoreWriter}
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.facet.FacetsConfig
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig, IndexableField}
import org.apache.lucene.search.{IndexSearcher, TopDocs}
import org.apache.lucene.store.{Directory, MMapDirectory}
import org.apache.lucene.search.{Query => LuceneQuery}
import java.util.ArrayList
import scala.jdk.CollectionConverters.*

trait Store() {
  def config: StoreConfig
  def mapping(indexName: String): IO[Option[IndexMapping]]
  def reader(index: IndexMapping): IO[Option[StoreReader]]
  def writer(index: IndexMapping): IO[StoreWriter]
  def refresh(index: IndexMapping): IO[Unit]
}

object Store extends Logging {
  val MAPPING_FILE_NAME = "mapping.json"

  case class StoreReader(
      mapping: IndexMapping,
      reader: IndexReader,
      dir: Directory,
      searcher: IndexSearcher,
      analyzer: Analyzer
  ) {
    def search(query: LuceneQuery, fields: List[String], n: Int): IO[List[Document]] = for {
      top  <- IO(searcher.search(query, n))
      docs <- collect(top, fields)
    } yield {
      docs
    }

    def search(query: Query, fields: List[String], n: Int): IO[List[Document]] = for {
      compiled <- query.compile(mapping)
      docs     <- search(compiled, fields, n)
    } yield {
      docs
    }

    def close(): IO[Unit] = info(s"closing index reader for index '${mapping.name}'") *> IO(reader.close())

    private def collect(top: TopDocs, fields: List[String]): IO[List[Document]] = IO {
      val fieldSet = fields.toSet
      val docs = top.scoreDocs.map(doc => {
        val visitor = DocumentVisitor(mapping, fieldSet)
        reader.storedFields().document(doc.doc, visitor)
        visitor.asDocument()
      })
      docs.toList
    }
  }

  object StoreReader {
    def create(dm: DirectoryMapping): IO[StoreReader] = IO {
      val reader = DirectoryReader.open(dm.dir)
      StoreReader(dm.mapping, reader, dm.dir, new IndexSearcher(reader), dm.analyzer)
    }
  }
  case class StoreWriter(
      mapping: IndexMapping,
      writer: IndexWriter,
      directory: MMapDirectory,
      fc: FacetsConfig,
      analyzer: Analyzer
  ) {
    import org.apache.lucene.document.{Document => LuceneDocument}
    def addDocuments(docs: List[Document]): Unit = {
      val all = new ArrayList[LuceneDocument]()
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
}
