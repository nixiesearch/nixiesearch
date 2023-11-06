package ai.nixiesearch.index.local

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, LexicalSearchLike}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.{Index, IndexReader, IndexWriter}
import cats.effect.kernel.Resource
import cats.effect.{IO, Ref}
import fs2.io.*
import fs2.io.file.{Files, Path}
import io.circe.parser.*
import io.circe.syntax.*
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.index.{DirectoryReader, IndexWriterConfig, IndexReader as LuceneIndexReader, IndexWriter as LuceneIndexWriter}
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, IOContext, MMapDirectory}

import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

case class LocalIndex(
    name: String,
    directory: Directory,
    analyzer: Analyzer,
    mappingRef: Ref[IO, IndexMapping],
    encoders: BiEncoderCache,
    readerRef: Ref[IO, DirectoryReader],
    searcherRef: Ref[IO, IndexSearcher],
    writer: LuceneIndexWriter,
    dirtyRef: Ref[IO, Boolean]
) extends Index
    with Logging {
  override def close(): IO[Unit] = for {
    mapping <- mappingRef.get
    _       <- info(s"closing index ${mapping.name}")
    _       <- readerRef.get.map(_.close())
    // _ <- writerRef.get.map(_.close())
    _ <- IO(directory.close())
  } yield {}
}

object LocalIndex extends Logging {
  import IndexMapping.json.given

  case class DirectoryMapping(dir: Directory, mapping: IndexMapping, analyzer: Analyzer)

  def create(config: LocalStoreConfig, mappingConfig: IndexMapping, encoders: BiEncoderCache): IO[LocalIndex] = for {
    _           <- ensureWorkdirExists(config.url.path)
    dm          <- openFileDirectory(config.url.path, mappingConfig)
    writer      <- IO(new LuceneIndexWriter(dm.dir, new IndexWriterConfig(dm.analyzer)))
    _           <- IO(writer.commit()) // to create empty segment
    reader      <- IO(DirectoryReader.open(writer))
    mappingRef  <- Ref.of[IO, IndexMapping](dm.mapping)
    readerRef   <- Ref.of[IO, DirectoryReader](reader)
    searcherRef <- Ref.of[IO, IndexSearcher](new IndexSearcher(reader))
    dirtyRef    <- Ref.of[IO, Boolean](false)
  } yield {
    LocalIndex(mappingConfig.name, dm.dir, dm.analyzer, mappingRef, encoders, readerRef, searcherRef, writer, dirtyRef)
  }

  def create(config: MemoryStoreConfig, mappingConfig: IndexMapping, encoders: BiEncoderCache): IO[LocalIndex] = for {
    dm          <- openMemoryDirectory(mappingConfig)
    writer      <- IO(new LuceneIndexWriter(dm.dir, new IndexWriterConfig(dm.analyzer)))
    _           <- IO(writer.commit()) // to create empty segment
    reader      <- IO(DirectoryReader.open(writer))
    readerRef   <- Ref.of[IO, DirectoryReader](reader)
    searcherRef <- Ref.of[IO, IndexSearcher](new IndexSearcher(reader))
    mappingRef  <- Ref.of[IO, IndexMapping](mappingConfig)
    dirtyRef    <- Ref.of[IO, Boolean](false)
  } yield {
    LocalIndex(mappingConfig.name, dm.dir, dm.analyzer, mappingRef, encoders, readerRef, searcherRef, writer, dirtyRef)
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

  def openFileDirectory(workdir: String, mapping: IndexMapping): IO[DirectoryMapping] = for {
    _             <- info(s"opening file directory for index ${mapping.name}")
    directory     <- IO(new MMapDirectory(Paths.get(workdir, mapping.name)))
    mappingExists <- IO(directory.listAll().contains(Index.MAPPING_FILE_NAME))
    mapping <- mappingExists match {
      case true  => readMapping(directory).flatMap(loaded => loaded.migrate(mapping))
      case false => writeMapping(mapping, directory) *> IO(mapping)
    }
    analyzer <- createAnalyzer(mapping)
  } yield {
    DirectoryMapping(directory, mapping, analyzer)
  }

  def openMemoryDirectory(mapping: IndexMapping): IO[DirectoryMapping] = for {
    _         <- info(s"opening in-memory directory for index ${mapping.name}")
    directory <- IO(new ByteBuffersDirectory())
    _         <- writeMapping(mapping, directory)
    analyzer  <- createAnalyzer(mapping)
  } yield {
    DirectoryMapping(directory, mapping, analyzer)
  }

  def createAnalyzer(mapping: IndexMapping): IO[Analyzer] = IO {
    val fieldAnalyzers = mapping.fields.values.collect {
      case TextFieldSchema(name, LexicalSearchLike(language), _, _, _, _)     => name -> language.analyzer
      case TextListFieldSchema(name, LexicalSearchLike(language), _, _, _, _) => name -> language.analyzer
    }
    new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldAnalyzers.toMap.asJava)
  }

  def readMapping(dir: Directory): IO[IndexMapping] = for {
    len <- IO(dir.fileLength(Index.MAPPING_FILE_NAME).toInt)
    buf <- IO.pure(new Array[Byte](len))
    mapping <- Resource
      .make(IO(dir.openInput(Index.MAPPING_FILE_NAME, IOContext.READ)))(input => IO(input.close()))
      .use(input =>
        for {
          _       <- IO(input.readBytes(buf, 0, len))
          decoded <- IO.fromEither(decode[IndexMapping](new String(buf)))
        } yield {
          decoded
        }
      )
  } yield {
    mapping
  }

  def writeMapping(mapping: IndexMapping, dir: Directory): IO[Unit] = {
    Resource
      .make(for {
        mappingExists <- IO(dir.listAll().contains(Index.MAPPING_FILE_NAME))
        _             <- IO.whenA(mappingExists)(IO(dir.deleteFile(Index.MAPPING_FILE_NAME)))
      } yield {
        dir.createOutput(Index.MAPPING_FILE_NAME, IOContext.DEFAULT)
      })(out => IO(out.close()))
      .use(output =>
        for {
          json <- IO(mapping.asJson.spaces2SortKeys.getBytes())
          _    <- info(s"wrote mapping file for an index ${mapping.name}")
          _    <- IO(output.writeBytes(json, json.length))
        } yield {}
      )
  }
}
