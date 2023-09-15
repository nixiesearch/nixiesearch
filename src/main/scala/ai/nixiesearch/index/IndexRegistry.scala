package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.{MapRef, Queue}
import cats.implicits.*

case class IndexRegistry(
    config: LocalStoreConfig,
    indices: MapRef[IO, String, Option[IndexMapping]],
    readers: MapRef[IO, String, Option[IndexReader]],
    writers: MapRef[IO, String, Option[IndexWriter]],
    shutdownHooks: Queue[IO, IO[Unit]],
    encoders: BiEncoderCache
) extends Logging {
  def mapping(index: String): IO[Option[IndexMapping]] = indices(index).get

  def reader(index: String): IO[Option[IndexReader]] = readers(index).get.flatMap {
    case Some(reader) => IO.some(reader)
    case None =>
      info(s"opening index $index for reading") *> indices(index).get.flatMap {
        case Some(mapping) =>
          for {
            mappingRef <- IO.pure(indices(index))
            tuple      <- LocalIndex(config, mappingRef, encoders).reader().allocated
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
      for {
        _ <- info(s"opening index $index for writing")
        mappingRef = indices(index.name)
        writerResource <- mappingRef.get.flatMap {
          case Some(value) => LocalIndex(config, mappingRef, encoders).writer().allocated
          case None =>
            for {
              _ <- mappingRef.set(Some(index))
              w <- LocalIndex(config, mappingRef, encoders).writer().allocated
            } yield {
              w
            }
        }
        (writer, close) = writerResource
        _ <- writers(index.name).set(Some(writer))
        _ <- shutdownHooks.offer(close)
      } yield {
        writer
      }
  }

  def close(): IO[Unit] = shutdownHooks.tryTake.flatMap {
    case Some(hook) => hook *> close()
    case None       => IO.unit
  }
}

object IndexRegistry extends Logging {
  def create(config: LocalStoreConfig, indices: List[IndexMapping]): Resource[IO, IndexRegistry] = for {
    indicesRefMap <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, IndexMapping]())
    _ <- Resource.eval(
      indices.traverse(mapping => indicesRefMap(mapping.name).update(_ => Some(mapping)))
    )
    readersRef <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, IndexReader]())
    writersRef <- Resource.eval(MapRef.ofConcurrentHashMap[IO, String, IndexWriter]())
    shutdown   <- Resource.eval(Queue.bounded[IO, IO[Unit]](1024))
    _          <- Resource.eval(info(s"Index registry initialized: ${indices.size} indices"))
    encoders   <- BiEncoderCache.create()
    modelsToPreload <- Resource.eval(IO(indices.flatMap(_.fields.values.collect {
      case TextLikeFieldSchema(_, SemanticSearchLikeType(model, _), _, _, _, _) => model
    })))
    _ <- Resource.eval(modelsToPreload.traverse(handle => encoders.get(handle)).void)
  } yield {
    IndexRegistry(
      config = config,
      indices = indicesRefMap,
      readers = readersRef,
      writers = writersRef,
      shutdownHooks = shutdown,
      encoders = encoders
    )
  }
}
