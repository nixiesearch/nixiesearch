package ai.nixiesearch.index

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.local.LocalIndex
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.index.{DirectoryReader, IndexReader as LuceneIndexReader}

trait Index extends IndexReader with IndexWriter {
  def name: String
  def mappingRef: Ref[IO, IndexMapping]
  def directory: Directory
  def analyzer: Analyzer
  def encoders: BiEncoderCache
  def searcherRef: Ref[IO, IndexSearcher]
  def readerRef: Ref[IO, DirectoryReader]
  def close(): IO[Unit]
}

object Index {
  val MAPPING_FILE_NAME = "mapping.json"

  def fromConfig(conf: StoreConfig, mappingConfig: IndexMapping, encoders: BiEncoderCache): IO[Index] =
    conf match {
      case s: LocalStoreConfig  => LocalIndex.create(s, mappingConfig, encoders)
      case m: MemoryStoreConfig => LocalIndex.create(m, mappingConfig, encoders)
      case other                => IO.raiseError(new Exception(s"store $other is not yet implemented"))
    }
}
