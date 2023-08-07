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
import ai.nixiesearch.index.store.rw.{StoreReader, StoreWriter}
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
}
