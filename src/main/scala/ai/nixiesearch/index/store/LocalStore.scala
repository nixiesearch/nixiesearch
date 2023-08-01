package ai.nixiesearch.index.store

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexBuilder
import ai.nixiesearch.index.store.LocalStore.DirectoryMapping
import ai.nixiesearch.index.store.Store.{StoreReader, StoreWriter}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter}
import cats.implicits.*
import fs2.io.file.{Files, Path}
import fs2.io.{readInputStream, writeOutputStream}
import io.circe.parser.*
import io.circe.syntax.*
import org.apache.lucene.store.{Directory, MMapDirectory}
import fs2.Stream

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths

case class LocalStore(
    config: LocalStoreConfig,
    dirs: Ref[IO, Map[String, DirectoryMapping]],
    readers: Ref[IO, Map[String, StoreReader]],
    writers: Ref[IO, Map[String, StoreWriter]]
) extends Store
    with Logging {
  override def reader(index: IndexMapping): IO[Option[StoreReader]] = for {
    cachedReaderMaybe <- readers.get.map(_.get(index.name))
    reader <- cachedReaderMaybe match {
      case Some(cachedreader) => IO.pure(Some(cachedreader))
      case None =>
        for {
          cachedDirMaybe <- dirs.get.map(_.get(index.name))
          reader <- cachedDirMaybe match {
            case Some(cachedDir) => StoreReader.create(cachedDir).map(Option.apply)
            case None =>
              for {
                indexPath   <- IO(List(config.url.path, index.name).mkString(File.separator))
                indexExists <- Files[IO].exists(Path(indexPath))
                result <- indexExists match {
                  case false => warn(s"index ${index.name} does not exist") *> IO.pure(None)
                  case true =>
                    for {
                      indexPathIsDirectory <- Files[IO].isDirectory(Path(indexPath))
                      _ <- IO.whenA(!indexPathIsDirectory)(
                        IO.raiseError(new Exception(s"index ${index.name} path is not a directory"))
                      )
                      dm     <- LocalStore.openDirectory(config.url.path, index)
                      _      <- dirs.update(_.updated(index.name, dm))
                      reader <- StoreReader.create(dm)
                      _      <- readers.update(_.updated(index.name, reader))
                      _      <- info(s"opened index reader for index '${index.name}'")
                    } yield {
                      Some(reader)
                    }
                }
              } yield {
                result
              }
          }
        } yield {
          reader
        }
    }
  } yield {
    reader
  }

  override def writer(index: IndexMapping): IO[Store.StoreWriter] = for {
    cachedWriterMaybe <- writers.get.map(_.get(index.name))
    writer <- cachedWriterMaybe match {
      case Some(cachedWriter) => IO.pure(cachedWriter)
      case None =>
        for {
          cachedDirMaybe <- dirs.get.map(_.get(index.name))
          writer <- cachedDirMaybe match {
            case Some(cachedDir) => StoreWriter.create(cachedDir)
            case None =>
              for {
                indexPath   <- IO(List(config.url.path, index.name).mkString(File.separator))
                indexExists <- Files[IO].exists(Path(indexPath))
                result <- indexExists match {
                  case false =>
                    for {
                      _      <- info(s"index dir ${index.name} does not exist, creating index writer")
                      _      <- Files[IO].createDirectory(Path(indexPath))
                      dir    <- LocalStore.openDirectory(config.url.path, index)
                      _      <- dirs.update(_.updated(index.name, dir))
                      writer <- StoreWriter.create(dir)
                      _      <- writers.update(_.updated(index.name, writer))
                      _      <- info(s"opened index writer for index '${index.name}'")
                    } yield {
                      writer
                    }
                  case true =>
                    for {
                      _      <- info(s"index dir ${index.name} exists, opening for writing")
                      dir    <- LocalStore.openDirectory(config.url.path, index)
                      _      <- dirs.update(_.updated(index.name, dir))
                      writer <- StoreWriter.create(dir)
                      _      <- writers.update(_.updated(index.name, writer))
                      _      <- info(s"opened index writer for index '${index.name}'")
                    } yield {
                      writer
                    }
                }
              } yield {
                result
              }
          }
        } yield {
          writer
        }
    }
  } yield {
    writer
  }

}

object LocalStore extends Logging {
  import IndexMapping.json.*

  case class DirectoryMapping(dir: Directory, mapping: IndexMapping)

  def create(config: LocalStoreConfig, indices: List[IndexMapping]): Resource[IO, LocalStore] = for {
    workdirPath   <- Resource.eval(IO(Path(config.url.path)))
    workdirExists <- Resource.eval(Files[IO].exists(workdirPath))
    workdirIsDir  <- Resource.eval(Files[IO].isDirectory(workdirPath))
    _ <- Resource.eval(
      IO.whenA(!workdirExists)(
        info(s"workdir ${config.url.path} does not exist, creating") *> Files[IO].createDirectory(workdirPath)
      )
    )
    _ <- Resource.eval(
      IO.whenA(workdirExists && !workdirIsDir)(
        IO.raiseError(new Exception(s"workdir ${config.url.path} should be a directory"))
      )
    )
    indexMappings <- Resource.eval(
      indices.traverse(index =>
        for {
          indexDir  <- IO(List(config.url.path, index.name).mkString(File.separator))
          indexPath <- IO.pure(Path(indexDir))
          exists    <- Files[IO].exists(indexPath)
          isDir     <- Files[IO].isDirectory(indexPath)
          _ <- IO.whenA(exists && isDir)(info(s"index ${index.name} local directory $indexDir found in the workdir"))
          _ <- IO.whenA(exists && !isDir)(
            IO.raiseError(new Exception(s"index ${index.name} path $indexDir should be a directory, but it's not"))
          )
          _ <- IO.whenA(!exists)(
            info(s"creating dir $indexDir for a new index ${index.name}") *> Files[IO].createDirectory(indexPath)
          )
        } yield {
          index
        }
      )
    )
    _ <- Resource.eval(info(s"Config contains following indices: [${indexMappings.map(_.name).mkString(",")}]"))
    luceneDirs <- Resource.eval(indexMappings.traverse(index => openDirectory(config.url.path, index)))
    luceneDirsRef <- Resource.eval(
      Ref.of[IO, Map[String, DirectoryMapping]](luceneDirs.map(dm => dm.mapping.name -> dm).toMap)
    )
    writersRef <- Resource.eval(Ref.of[IO, Map[String, StoreWriter]](Map.empty))
    readerRef  <- Resource.eval(Ref.of[IO, Map[String, StoreReader]](Map.empty))
  } yield {
    LocalStore(config, luceneDirsRef, readerRef, writersRef)
  }

  private def openDirectory(workdir: String, mapping: IndexMapping): IO[DirectoryMapping] = for {
    _             <- info(s"opening index ${mapping.name}")
    mappingPath   <- IO(List(workdir, mapping.name, IndexBuilder.MAPPING_FILE_NAME).mkString(File.separator))
    mappingExists <- Files[IO].exists(Path(mappingPath))
    mapping <- mappingExists match {
      case true =>
        readInputStream(IO(new FileInputStream(new File(mappingPath))), 1024)
          .through(fs2.text.utf8.decode)
          .compile
          .toList
          .map(_.mkString)
          .flatMap(json => IO.fromEither(decode[IndexMapping](json)))
          .flatMap(loaded => loaded.migrate(mapping))
      case false =>
        Stream(mapping.asJson.spaces2SortKeys.getBytes(): _*)
          .through(writeOutputStream(IO(new FileOutputStream(new File(mappingPath)))))
          .compile
          .drain
          .flatMap(_ => info(s"wrote mapping file $mappingPath for a new index ${mapping.name}") *> IO(mapping))
    }
    directory <- IO(new MMapDirectory(Paths.get(workdir, mapping.name)))
  } yield {
    DirectoryMapping(directory, mapping)
  }

}
