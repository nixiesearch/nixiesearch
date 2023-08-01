package ai.nixiesearch.index

import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.index.IndexWriter
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document

import java.util.ArrayList
import org.apache.lucene.index.IndexableField
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.Field.TextListField
import ai.nixiesearch.core.Field.IntField
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.codec.TextFieldWriter
import ai.nixiesearch.core.codec.TextListFieldWriter
import ai.nixiesearch.core.codec.IntFieldWriter
import cats.effect.IO
import fs2.io.{readInputStream, writeOutputStream}
import fs2.Stream

import java.nio.file.{Path, Paths}
import org.apache.lucene.index.IndexWriterConfig
import io.circe.syntax.*
import io.circe.parser.*

import java.io.{File, FileInputStream, FileOutputStream}

case class IndexBuilder(dir: MMapDirectory, writer: IndexWriter, schema: IndexMapping) extends Logging {
  def addDocuments(docs: List[Document]): Unit = {
    val all = new ArrayList[ArrayList[IndexableField]]()
    docs.foreach(doc => {
      val buffer = new ArrayList[IndexableField](8)
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          schema.textFields.get(name) match {
            case None          => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) => TextFieldWriter().write(field, mapping, buffer)
          }
        case field @ TextListField(name, value) =>
          schema.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => TextListFieldWriter().write(field, mapping, buffer)
          }
        case field @ IntField(name, value) =>
          schema.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => IntFieldWriter().write(field, mapping, buffer)
          }
      }
      all.add(buffer)
    })
    writer.addDocuments(all)
  }
}

object IndexBuilder extends Logging {
  val MAPPING_FILE_NAME = "mapping.json"
  import IndexMapping.json.*

  def create(workPath: Path, mapping: IndexMapping): IO[IndexBuilder] = for {
    workPathFile <- IO(workPath.toFile)
    _ <- IO.whenA(!workPathFile.exists())(
      IO(workPathFile.mkdirs()) *> info(s"work directory $workPath is missing, creating.")
    )
    indexPathFile <- IO(new File(workPathFile.toString + File.separator + mapping.name))
    _ <- IO.whenA(!indexPathFile.exists())(
      IO(indexPathFile.mkdirs()) *> info(s"index dir $indexPathFile is missing, creating.")
    )
    mappingFile <- IO(new File(indexPathFile.toString + File.separator + MAPPING_FILE_NAME))
    _ <- Stream(mapping.asJson.spaces2SortKeys.getBytes: _*)
      .through(writeOutputStream[IO](IO(new FileOutputStream(mappingFile))))
      .compile
      .drain
    builder <- open(indexPathFile.toPath)
  } yield {
    builder
  }

  def open(indexPath: Path): IO[IndexBuilder] = for {
    _           <- info(s"opening index $indexPath")
    directory   <- IO(new MMapDirectory(indexPath))
    config      <- IO(new IndexWriterConfig())
    writer      <- IO(new IndexWriter(directory, config))
    mappingFile <- IO(new File(indexPath.toString + File.separator + MAPPING_FILE_NAME))
    mapping <- readInputStream[IO](IO(new FileInputStream(mappingFile)), 1024)
      .through(fs2.text.utf8.decode)
      .reduce(_ + _)
      .compile
      .toList
      .map(_.mkString(""))
      .flatMap(json => IO.fromEither(decode[IndexMapping](json)))
  } yield {
    new IndexBuilder(directory, writer, mapping)
  }
}
