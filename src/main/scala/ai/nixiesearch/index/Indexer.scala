package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.{FieldSchema, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.{
  DoubleFieldWriter,
  FieldWriter,
  FloatFieldWriter,
  IntFieldWriter,
  LongFieldWriter,
  TextFieldWriter,
  TextListFieldWriter
}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.sync.ReplicatedIndex
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.store.{Directory, MMapDirectory}
import org.apache.lucene.document.Document as LuceneDocument

import java.util
import cats.implicits.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, TermQuery}

import scala.collection.mutable.ArrayBuffer

case class Indexer(index: ReplicatedIndex, writer: IndexWriter) extends Logging {

  lazy val textFieldWriter     = TextFieldWriter()
  lazy val textListFieldWriter = TextListFieldWriter()
  lazy val intFieldWriter      = IntFieldWriter()
  lazy val longFieldWriter     = LongFieldWriter()
  lazy val floatFieldWriter    = FloatFieldWriter()
  lazy val doubleFieldWriter   = DoubleFieldWriter()

  def addDocuments(docs: List[Document]): IO[Unit] = {
    for {
      _               <- debug(s"adding ${docs.size} docs to index '${index.name}'")
      handles         <- IO(index.mapping.modelHandles())
      fieldStrings    <- IO(strings(index.mapping, docs))
      embeddedStrings <- embed(fieldStrings, index.encoders)
    } yield {
      val all = new util.ArrayList[LuceneDocument]()
      val ids = new ArrayBuffer[String]()
      docs.foreach(doc => {
        val buffer = new LuceneDocument()
        doc.fields.foreach {
          case field @ TextField(name, value) =>
            index.mapping.textFields.get(name) match {
              case None => logger.warn(s"text field '$name' is not defined in mapping")
              case Some(mapping) =>
                if (name == "_id") ids.addOne(value)
                mapping match {
                  case TextFieldSchema(_, tpe: SemanticSearchLikeType, _, _, _, _) =>
                    textFieldWriter.write(field, mapping, buffer, embeddedStrings.getOrElse(tpe, Map.empty))
                  case _ => textFieldWriter.write(field, mapping, buffer, Map.empty)
                }

            }
          case field @ TextListField(name, value) =>
            index.mapping.textListFields.get(name) match {
              case None          => logger.warn(s"text[] field '$name' is not defined in mapping")
              case Some(mapping) => textListFieldWriter.write(field, mapping, buffer, Map.empty)
            }
          case field @ IntField(name, value) =>
            index.mapping.intFields.get(name) match {
              case None          => logger.warn(s"int field '$name' is not defined in mapping")
              case Some(mapping) => intFieldWriter.write(field, mapping, buffer)
            }
          case field @ LongField(name, value) =>
            index.mapping.longFields.get(name) match {
              case None          => logger.warn(s"long field '$name' is not defined in mapping")
              case Some(mapping) => longFieldWriter.write(field, mapping, buffer)
            }
          case field @ FloatField(name, value) =>
            index.mapping.floatFields.get(name) match {
              case None          => // logger.warn(s"float field '$name' is not defined in mapping")
              case Some(mapping) => floatFieldWriter.write(field, mapping, buffer)
            }
          case field @ DoubleField(name, value) =>
            index.mapping.doubleFields.get(name) match {
              case None          => logger.warn(s"double field '$name' is not defined in mapping")
              case Some(mapping) => doubleFieldWriter.write(field, mapping, buffer)
            }
        }
        all.add(buffer)
      })
      val deleteIds = ids.map(id => new Term("_id_raw", id))
      writer.deleteDocuments(deleteIds.toSeq: _*)
      writer.addDocuments(all)
    }
  }

  def strings(mapping: IndexMapping, docs: List[Document]): Map[SemanticSearchLikeType, List[String]] = {
    val candidates = for {
      doc   <- docs
      field <- doc.fields
      model <- mapping.fields.get(field.name).toList.flatMap {
        case TextLikeFieldSchema(name, tpe: SemanticSearchLikeType, _, _, _, _) =>
          Some(tpe)
        case other =>
          None
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
          encoded <- strings.distinct
            .sortBy(-_.length)
            .grouped(8)
            .toList
            .flatTraverse(batch =>
              encoders
                .encode(tpe.model, batch.map(s => tpe.prefix.document + s))
                .flatMap(embeddings => IO(batch.zip(embeddings)))
            )
        } yield {
          tpe -> encoded.toMap
        }
      }
      .map(_.toMap)
  }

  def flush(): IO[Unit] = for {
    _        <- debug("index commit")
    seqnum   <- IO(writer.commit())
    manifest <- index.master.createManifest(index.mapping, seqnum)
    _        <- index.master.writeManifest(manifest)
    _        <- index.sync()
  } yield {}

  def close(): IO[Unit] = flush() *> IO(writer.close())
}

object Indexer {
  def open(index: ReplicatedIndex): Resource[IO, Indexer] = {
    val make = for {
      analyzer <- IO(IndexMapping.createAnalyzer(index.mapping))
      config   <- IO(new IndexWriterConfig(analyzer))
      writer   <- IO(new IndexWriter(index.directory, config))
      niw      <- IO.pure(Indexer(index, writer))
      _        <- niw.flush()
    } yield {
      niw
    }
    Resource.make(make)(niw => niw.close())
  }
}
