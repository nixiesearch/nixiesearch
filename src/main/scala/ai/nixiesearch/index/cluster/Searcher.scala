package ai.nixiesearch.index.cluster

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{Index, NixieIndexSearcher, NixieIndexWriter}
import ai.nixiesearch.util.RefMap
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import cats.effect.std.MapRef
import org.apache.lucene.store.{ByteBuffersDirectory, Directory}
import org.apache.lucene.index.{DirectoryReader, IndexWriter}
import org.apache.lucene.search.IndexSearcher
import cats.implicits.*
import fs2.Stream
import ai.nixiesearch.index.cluster.Searcher.IndexNotFoundException
import ai.nixiesearch.index.manifest.IndexManifest

import scala.concurrent.duration.*

case class Searcher(indices: RefMap[String, NixieIndexSearcher]) extends Logging {
  def search(indexName: String, request: SearchRequest): IO[SearchResponse] = for {
    indexOption <- indices.get(indexName)
    index       <- IO.fromOption(indexOption)(IndexNotFoundException(indexName))
    response    <- index.search(request)
  } yield {
    response
  }
  def sync(): IO[Unit] = {
    Stream
      .evalSeq(indices.values())
      .evalMap(searchIndex =>
        for {
          currentSeqnum  <- searchIndex.seqnumRef.get
          manifestOption <- IndexManifest.read(searchIndex.index.dir)
          _ <- manifestOption match {
            case Some(manifest) =>
              IO.whenA(manifest.seqnum != currentSeqnum)(for {
                _      <- info(s"index sync: current=$currentSeqnum directory=${manifest.seqnum}")
                reader <- searchIndex.readerRef.updateAndGet(r => DirectoryReader.openIfChanged(r))
                _      <- searchIndex.searcherRef.update(s => new IndexSearcher(reader))
                _      <- searchIndex.seqnumRef.set(manifest.seqnum)
              } yield {})
            case None => IO.unit
          }
        } yield {}
      )
      .compile
      .drain
  }

  def close(): IO[Unit] = ???
}

object Searcher {
  case class IndexNotFoundException(name: String) extends Throwable(s"index $name not found")

  def open(indices: List[Index]): IO[Searcher] = for {
    searchIndices <- indices
      .map(index => NixieIndexSearcher.open(index).flatMap(si => IO.pure(index.name -> si)))
      .sequence
    indicesMap <- RefMap.of(searchIndices.toMap)
  } yield {
    Searcher(indices = indicesMap)
  }

}
