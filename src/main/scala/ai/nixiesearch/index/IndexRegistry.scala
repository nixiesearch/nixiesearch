package ai.nixiesearch.index

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.{MapRef, Queue}
import cats.implicits.*

case class IndexRegistry(
    config: LocalStoreConfig,
    indices: MapRef[IO, String, Option[Index]],
    readers: MapRef[IO, String, Option[IndexReader]],
    writers: MapRef[IO, String, Option[IndexWriter]],
    shutdownHooks: Queue[IO, IO[Unit]]
) extends Logging {
  def mapping(index: String): IO[Option[IndexMapping]] = indices(index).get.map(_.map(_.mapping))

  def reader(index: String): IO[Option[IndexReader]] = readers(index).get.flatMap {
    case Some(reader) => IO.some(reader)
    case None =>
      info(s"opening index $index for reading") *> indices(index).get.flatMap {
        case Some(ind) =>
          for {
            tuple <- ind.reader().allocated
            (reader, readerClose) = tuple
            _ <- readers(index).set(Some(reader))
            _ <- shutdownHooks.offer(readerClose)
          } yield {
            Some(reader)
          }
        case None => ???
      }
  }

  def writer(index: IndexMapping): IO[IndexWriter] = writers(index.name).get.flatMap {
    case Some(writer) => IO.pure(writer)
    case None =>
      info(s"opening index $index for writing") *> indices(index.name).get.flatMap {
        case Some(ind) =>
          for {
            tuple <- ind.writer().allocated
            (writer, close) = tuple
            _ <- writers(index.name).set(Some(writer))
            _ <- shutdownHooks.offer(close)
          } yield {
            writer
          }
        case None =>
          for {
            _     <- info(s"creating index $index")
            ind   <- IO.pure(LocalIndex(config, index))
            _     <- indices(index.name).set(Some(ind))
            tuple <- ind.writer().allocated
            (writer, close) = tuple
            _ <- writers(index.name).set(Some(writer))
            _ <- shutdownHooks.offer(close)
          } yield {
            writer
          }

      }
  }

  def close(): IO[Unit] = shutdownHooks.tryTake.flatMap {
    case Some(hook) => hook *> close()
    case None       => IO.unit
  }
}

object IndexRegistry {
  def create(config: LocalStoreConfig, indices: List[IndexMapping]): Resource[IO, IndexRegistry] = for {
    indicesRef <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, Index]())
    _ <- Resource.eval(
      indices.traverse(mapping => indicesRef(mapping.name).update(_ => Some(LocalIndex(config, mapping))))
    )
    readersRef <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, IndexReader]())
    writersRef <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, IndexWriter]())
    shutdown   <- Resource.eval(Queue.bounded[IO, IO[Unit]](1024))
  } yield {
    IndexRegistry(
      config = config,
      indices = indicesRef,
      readers = readersRef,
      writers = writersRef,
      shutdownHooks = shutdown
    )
  }
}
