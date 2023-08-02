package ai.nixiesearch.index.store

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Logging
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
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths

import scala.jdk.CollectionConverters.*

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
  def mapping(indexName: String): IO[Option[IndexMapping]] = for {
    dirMaybe <- dirs.get.map(_.get(indexName))
  } yield {
    dirMaybe.map(_.mapping)
  }

  override def refresh(index: IndexMapping): IO[Unit] = for {
    _           <- info(s"mapping changed for index '${index.name}'")
    mappingPath <- IO(List(config.url.path, index.name, Store.MAPPING_FILE_NAME).mkString(File.separator))
    _           <- LocalStore.writeMapping(index, config.url.path)
    _           <- updateMapping[DirectoryMapping](dirs, index.name, _.copy(mapping = index))
    _           <- updateMapping[StoreWriter](writers, index.name, _.copy(mapping = index))
    _           <- updateMapping[StoreReader](readers, index.name, _.copy(mapping = index))
  } yield {}

  private def updateMapping[T](cache: Ref[IO, Map[String, T]], index: String, update: T => T): IO[Unit] =
    cache.update(d =>
      d.get(index) match {
        case Some(prev) => d.updated(index, update(prev))
        case None       => d
      }
    )

}

object LocalStore extends Logging {
  import IndexMapping.json.*

  case class DirectoryMapping(dir: MMapDirectory, mapping: IndexMapping, analyzer: Analyzer) {
    def close(): IO[Unit] = IO(dir.close())
  }

  def create(config: LocalStoreConfig, indices: List[IndexMapping]): Resource[IO, LocalStore] = for {
    _ <- Resource.eval(ensureWorkdirExists(config.url.path))
    indexMappings <- Resource.eval(
      indices.traverse(index => ensureIndexDirExists(config.url.path, index.name).map(_ => index))
    )
    _ <- Resource.eval(info(s"Config contains following indices: [${indexMappings.map(_.name).mkString(",")}]"))
    luceneDirs <- Resource
      .make(indexMappings.traverse(index => openDirectory(config.url.path, index)))(x => x.traverse(_.close()).void)
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
    mappingPath   <- IO(List(workdir, mapping.name, Store.MAPPING_FILE_NAME).mkString(File.separator))
    mappingExists <- Files[IO].exists(Path(mappingPath))
    mapping <- mappingExists match {
      case true  => readMapping(mappingPath).flatMap(loaded => loaded.migrate(mapping))
      case false => writeMapping(mapping, workdir) *> IO(mapping)
    }
    directory <- IO(new MMapDirectory(Paths.get(workdir, mapping.name)))
    analyzer  <- createAnalyzer(mapping)
  } yield {

    DirectoryMapping(directory, mapping, analyzer)
  }

  private def createAnalyzer(mapping: IndexMapping): IO[Analyzer] = IO {
    val fieldAnalyzers = mapping.fields.values.collect {
      case TextFieldSchema(name, LexicalSearch(language), _, _, _, _)     => name -> language.analyzer
      case TextListFieldSchema(name, LexicalSearch(language), _, _, _, _) => name -> language.analyzer
    }
    new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldAnalyzers.toMap.asJava)
  }

  private def writeMapping(mapping: IndexMapping, workdir: String): IO[Unit] = {
    for {
      _           <- ensureWorkdirExists(workdir)
      _           <- ensureIndexDirExists(workdir, mapping.name)
      mappingPath <- IO(List(workdir, mapping.name, Store.MAPPING_FILE_NAME).mkString(File.separator))
      _ <- Stream(mapping.asJson.spaces2SortKeys.getBytes(): _*)
        .through(writeOutputStream(IO(new FileOutputStream(new File(mappingPath)))))
        .compile
        .drain
        .flatMap(_ => info(s"wrote mapping file $mappingPath for an index ${mapping.name}"))
    } yield {}
  }

  private def readMapping(file: String): IO[IndexMapping] =
    readInputStream(IO(new FileInputStream(new File(file))), 1024)
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)
      .flatMap(json => IO.fromEither(decode[IndexMapping](json)))

  private def ensureWorkdirExists(workdir: String): IO[Unit] = for {
    workdirPath   <- IO(Path(workdir))
    workdirExists <- Files[IO].exists(workdirPath)
    workdirIsDir  <- Files[IO].isDirectory(workdirPath)
    _ <- IO.whenA(!workdirExists)(
      info(s"workdir $workdir does not exist, creating") *> Files[IO].createDirectory(workdirPath)
    )
    _ <- IO.whenA(workdirExists && !workdirIsDir)(
      IO.raiseError(new Exception(s"workdir $workdir should be a directory"))
    )
  } yield {}

  private def ensureIndexDirExists(workdir: String, index: String): IO[Unit] = for {
    indexDir  <- IO(List(workdir, index).mkString(File.separator))
    indexPath <- IO.pure(Path(indexDir))
    exists    <- Files[IO].exists(indexPath)
    isDir     <- Files[IO].isDirectory(indexPath)
    _         <- IO.whenA(exists && isDir)(info(s"index $index local directory $indexDir found in the workdir"))
    _ <- IO.whenA(exists && !isDir)(
      IO.raiseError(new Exception(s"index $index path $indexDir should be a directory, but it's not"))
    )
    _ <- IO.whenA(!exists)(
      info(s"creating dir $indexDir for a new index $index") *> Files[IO].createDirectory(indexPath)
    )

  } yield {}
}
