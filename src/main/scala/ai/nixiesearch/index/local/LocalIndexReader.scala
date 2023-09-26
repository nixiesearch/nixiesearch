package ai.nixiesearch.index.local

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexReader
import cats.effect.{IO, Ref}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.index.IndexReader as LuceneIndexReader

case class LocalIndexReader(
    name: String,
    config: LocalStoreConfig,
    mappingRef: Ref[IO, Option[IndexMapping]],
    reader: LuceneIndexReader,
    dir: Directory,
    searcher: IndexSearcher,
    analyzer: Analyzer,
    encoders: BiEncoderCache
) extends IndexReader
