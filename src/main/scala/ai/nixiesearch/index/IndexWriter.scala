package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.{FieldSchema, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.{FieldWriter, FloatFieldWriter, IntFieldWriter, TextFieldWriter, TextListFieldWriter}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.BiEncoderCache
import cats.effect.{IO, Ref}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.IndexWriter as LuceneIndexWriter
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.document.Document as LuceneDocument

import java.util
import cats.implicits.*

trait IndexWriter extends Logging {
  def name: String
  def config: StoreConfig
  def mappingRef: Ref[IO, Option[IndexMapping]]
  def refreshMapping(mapping: IndexMapping): IO[Unit]
  def writer: LuceneIndexWriter
  def directory: MMapDirectory
  def analyzer: Analyzer
  def encoders: BiEncoderCache

  lazy val textFieldWriter     = TextFieldWriter()
  lazy val textListFieldWriter = TextListFieldWriter()
  lazy val intFieldWriter      = IntFieldWriter()
  lazy val floatFieldWriter    = FloatFieldWriter()

  def mapping(): IO[IndexMapping] = mappingRef.get.flatMap {
    case Some(value) => IO.pure(value)
    case None        => IO.raiseError(new Exception("this should never happen"))
  }

  def addDocuments(docs: List[Document]): IO[Unit] = for {
    mapping <- mapping()
    handles <- IO(mapping.textFields.values.toList.collect {
      case TextFieldSchema(_, SemanticSearchLikeType(handle, _), _, _, _, _) =>
        handle
    })
    fieldStrings    <- IO(strings(mapping, docs))
    embeddedStrings <- embed(fieldStrings, encoders)
  } yield {
    val all = new util.ArrayList[LuceneDocument]()
    docs.foreach(doc => {
      val buffer = new LuceneDocument()
      doc.fields.foreach {
        case field @ TextField(name, _) =>
          mapping.textFields.get(name) match {
            case None => logger.warn(s"text field '$name' is not defined in mapping")
            case Some(mapping) =>
              mapping match {
                case TextFieldSchema(_, tpe: SemanticSearchLikeType, _, _, _, _) =>
                  textFieldWriter.write(field, mapping, buffer, embeddedStrings.getOrElse(tpe, Map.empty))
                case _ => textFieldWriter.write(field, mapping, buffer, Map.empty)
              }

          }
        case field @ TextListField(name, value) =>
          mapping.textListFields.get(name) match {
            case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
            case Some(mapping) => textListFieldWriter.write(field, mapping, buffer, Map.empty)
          }
        case field @ IntField(name, value) =>
          mapping.intFields.get(name) match {
            case None          => logger.warn(s"int field '$name' is not defined in mapping")
            case Some(mapping) => intFieldWriter.write(field, mapping, buffer)
          }
        case field @ FloatField(name, value) =>
          mapping.floatFields.get(name) match {
            case None          => logger.warn(s"float field '$name' is not defined in mapping")
            case Some(mapping) => floatFieldWriter.write(field, mapping, buffer)
          }
      }
      all.add(buffer)
    })
    writer.addDocuments(all)
  }

  def strings(mapping: IndexMapping, docs: List[Document]): Map[SemanticSearchLikeType, List[String]] = {
    val candidates = for {
      doc   <- docs
      field <- doc.fields
      model <- mapping.fields.get(field.name).toList.collect {
        case TextLikeFieldSchema(name, tpe: SemanticSearchLikeType, _, _, _, _) => tpe
      }
      string <- field match {
        case TextField(name, value)      => List(value)
        case TextListField(name, values) => values
        case _                           => Nil
      }
    } yield {
      model -> string
    }
    candidates.groupMap(_._1)(_._2)
  }

  def embed(
      targets: Map[SemanticSearchLikeType, List[String]],
      encoders: BiEncoderCache
  ): IO[Map[SemanticSearchLikeType, Map[String, Array[Float]]]] = {
    targets.toList
      .traverse { case (tpe, strings) =>
        for {
          encoder <- encoders.get(tpe.model)
          encoded <- strings
            .grouped(64)
            .toList
            .flatTraverse(batch => IO(batch.zip(encoder.embed(batch.map(s => tpe.prefix.document + s).toArray))))
        } yield {
          tpe -> encoded.toMap
        }
      }
      .map(_.toMap)
  }

  def flush(): IO[Unit] = IO(writer.commit())

  def close(): IO[Unit] = for {
    m <- mapping()
    _ <- info(s"closing index writer for index '${m.name}'")
    _ <- IO(writer.close())
  } yield {}

}
