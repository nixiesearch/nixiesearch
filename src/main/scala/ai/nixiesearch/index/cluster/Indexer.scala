package ai.nixiesearch.index.cluster

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.cluster.Searcher.IndexNotFoundException
import ai.nixiesearch.index.{Index, NixieIndexSearcher, NixieIndexWriter}
import ai.nixiesearch.util.RefMap
import cats.effect.IO
import cats.implicits.given

case class Indexer(indices: RefMap[String, NixieIndexWriter]) extends Logging {
  def index(indexName: String, batch: List[Document]): IO[Unit] = for {
    indexOption <- indices.get(indexName)
    index       <- IO.fromOption(indexOption)(IndexNotFoundException(indexName))
    _           <- index.addDocuments(batch)
    _           <- debug(s"for index '$indexName' indexed batch of ${batch.size} docs")
  } yield {}

  def mapping(indexName: String): IO[IndexMapping] = for {
    indexOption <- indices.get(indexName)
    index       <- IO.fromOption(indexOption)(IndexNotFoundException(indexName))
  } yield {
    index.index.mapping
  }

  def flush(indexName: String): IO[Unit] = for {
    indexOption <- indices.get(indexName)
    index       <- IO.fromOption(indexOption)(IndexNotFoundException(indexName))
    _           <- index.flush()
  } yield {}

  def close(): IO[Unit] = for {
    indexList <- indices.values()
    _         <- fs2.Stream.emits(indexList).evalMap(_.close()).compile.drain
  } yield {}
}

object Indexer {
  def create(indices: List[Index]): IO[Indexer] = for {
    indexWriters <- indices.map(index => NixieIndexWriter.create(index).map(iw => index.name -> iw)).sequence
    indexMap     <- RefMap.of(indexWriters.toMap)
  } yield {
    Indexer(indexMap)
  }
}
