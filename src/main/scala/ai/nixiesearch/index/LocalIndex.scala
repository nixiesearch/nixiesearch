package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.IndexMapping.json.indexMappingDecoder
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.{IndexReader, IndexWriter}
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import fs2.io.file.{Files, Path}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.{Directory, MMapDirectory}
import org.apache.lucene.index.{
  DirectoryReader,
  IndexWriterConfig,
  IndexReader as LuceneIndexReader,
  IndexWriter as LuceneIndexWriter
}
import fs2.Stream
import fs2.io.*
import io.circe.syntax.*
import io.circe.parser.*

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

case class LocalIndex(config: LocalStoreConfig, mappingRef: Ref[IO, Option[IndexMapping]], encoders: BiEncoderCache)
    extends Index
    with Logging {
  import LocalIndex.*

  def getMapping() = Resource.eval(mappingRef.get.flatMap {
    case Some(value) => IO.pure(value)
    case None        => IO.raiseError(new Exception("nope"))
  })
  override def writer(): Resource[IO, LocalIndexWriter] = for {
    mapping <- getMapping()
    _       <- Resource.eval(ensureWorkdirExists(config.url.path))
    indexMappings <- Resource.eval(
      ensureIndexDirExists(
        config.url.path,
        mapping.name,
        whenMissing = indexPath => info(s"creating index dir $indexPath") *> Files[IO].createDirectory(indexPath)
      )
    )
    luceneDir    <- Resource.make(openDirectory(config.url.path, mapping))(_.close())
    writerConfig <- Resource.pure(new IndexWriterConfig(luceneDir.analyzer))
    writer       <- Resource.eval(IO(LuceneIndexWriter(luceneDir.dir, writerConfig)))
    _            <- Resource.eval(IO(writer.commit()))
  } yield {
    LocalIndexWriter(
      name = mapping.name,
      config = config,
      mappingRef = mappingRef,
      writer = writer,
      directory = luceneDir.dir,
      analyzer = luceneDir.analyzer,
      encoders = encoders
    )
  }

  override def reader(): Resource[IO, LocalIndexReader] = for {
    mapping <- getMapping()
    _       <- Resource.eval(ensureWorkdirExists(config.url.path))
    _ <- Resource.eval(
      ensureIndexDirExists(
        config.url.path,
        mapping.name,
        whenMissing =
          indexPath => IO.raiseError(new Exception(s"Cannot create reader for a never written index $indexPath"))
      )
    )
    luceneDir <- Resource.make(openDirectory(config.url.path, mapping))(_.close())
    reader    <- Resource.eval(IO(DirectoryReader.open(luceneDir.dir)))
  } yield {
    LocalIndexReader(
      name = mapping.name,
      config = config,
      mappingRef = mappingRef,
      reader = reader,
      dir = luceneDir.dir,
      searcher = new IndexSearcher(reader),
      analyzer = luceneDir.analyzer,
      encoders = encoders
    )
  }

}

object LocalIndex extends Logging {
  import IndexMapping.json.given

  case class DirectoryMapping(dir: MMapDirectory, mapping: IndexMapping, analyzer: Analyzer) {
    def close(): IO[Unit] = IO(dir.close())
  }

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
      _ <- writeMapping(mapping, config.url.path)
      _ <- info(s"mapping updated for index ${mapping.name}")
    } yield {}

  }

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

  private def ensureIndexDirExists(workdir: String, index: String, whenMissing: Path => IO[Unit]): IO[Unit] = for {
    indexDir  <- IO(List(workdir, index).mkString(File.separator))
    indexPath <- IO.pure(Path(indexDir))
    exists    <- Files[IO].exists(indexPath)
    isDir     <- Files[IO].isDirectory(indexPath)
    _         <- IO.whenA(exists && isDir)(info(s"index $index local directory $indexDir found in the workdir"))
    _ <- IO.whenA(exists && !isDir)(
      IO.raiseError(new Exception(s"index $index path $indexDir should be a directory, but it's not"))
    )
    _ <- IO.whenA(!exists)(whenMissing(indexPath))

  } yield {}

  private def openDirectory(workdir: String, mapping: IndexMapping): IO[DirectoryMapping] = for {
    _             <- info(s"opening directory ${mapping.name}")
    mappingPath   <- IO(List(workdir, mapping.name, Index.MAPPING_FILE_NAME).mkString(File.separator))
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

  private def readMapping(file: String): IO[IndexMapping] =
    readInputStream(IO(new FileInputStream(new File(file))), 1024)
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)
      .flatMap(json => IO.fromEither(decode[IndexMapping](json)))

  private def writeMapping(mapping: IndexMapping, workdir: String): IO[Unit] = {
    for {
      _ <- ensureWorkdirExists(workdir)
      _ <- ensureIndexDirExists(
        workdir,
        mapping.name,
        _ => IO.raiseError(new Exception("cannot update mapping"))
      )
      mappingPath <- IO(List(workdir, mapping.name, Index.MAPPING_FILE_NAME).mkString(File.separator))
      _ <- Stream(mapping.asJson.spaces2SortKeys.getBytes(): _*)
        .through(writeOutputStream(IO(new FileOutputStream(new File(mappingPath)))))
        .compile
        .drain
        .flatMap(_ => info(s"wrote mapping file $mappingPath for an index ${mapping.name}"))
    } yield {}
  }
}
