package ai.nixiesearch.index.local

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexWriter
import cats.effect.{IO, Ref}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{
  DirectoryReader,
  IndexWriterConfig,
  IndexReader as LuceneIndexReader,
  IndexWriter as LuceneIndexWriter
}
import org.apache.lucene.store.MMapDirectory

case class LocalIndexWriter(
    name: String,
    config: LocalStoreConfig,
    mappingRef: Ref[IO, Option[IndexMapping]],
    writer: LuceneIndexWriter,
    directory: MMapDirectory,
    analyzer: Analyzer,
    encoders: BiEncoderCache
) extends IndexWriter
    with Logging {
  override def refreshMapping(mapping: IndexMapping): IO[Unit] = for {
    _ <- mappingRef.set(Some(mapping))
    _ <- LocalIndex.writeMapping(mapping, config.url.path)
    _ <- info(s"mapping updated for index ${mapping.name}")
  } yield {}

}
