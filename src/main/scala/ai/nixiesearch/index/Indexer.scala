package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.{
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextLikeFieldSchema
}
import ai.nixiesearch.config.{FieldSchema, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.{SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.{
  BooleanFieldWriter,
  DoubleFieldWriter,
  FieldWriter,
  FloatFieldWriter,
  IntFieldWriter,
  LongFieldWriter,
  NixiesearchCodec,
  TextFieldWriter,
  TextListFieldWriter
}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.embedding.EmbedderDict
import ai.nixiesearch.index.sync.{Index, ReplicatedIndex}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.store.{Directory, MMapDirectory}
import org.apache.lucene.document.Document as LuceneDocument

import scala.concurrent.duration.*
import java.util
import cats.implicits.*
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, TermQuery}
import fs2.Stream
import org.apache.lucene.codecs.FilterCodec
import org.apache.lucene.codecs.lucene99.Lucene99Codec
import org.apache.lucene.search.suggest.document.Completion99PostingsFormat

import scala.collection.mutable.ArrayBuffer

case class Indexer(index: Index, writer: IndexWriter) extends Logging {

  lazy val textFieldWriter     = TextFieldWriter()
  lazy val textListFieldWriter = TextListFieldWriter()
  lazy val intFieldWriter      = IntFieldWriter()
  lazy val longFieldWriter     = LongFieldWriter()
  lazy val floatFieldWriter    = FloatFieldWriter()
  lazy val doubleFieldWriter   = DoubleFieldWriter()
  lazy val boolFieldWriter     = BooleanFieldWriter()

  def addDocuments(docs: List[Document]): IO[Unit] = {
    for {
      _               <- debug(s"adding ${docs.size} docs to index '${index.name.value}'")
      handles         <- IO(index.mapping.modelHandles())
      fieldStrings    <- IO(strings(index.mapping, docs))
      embeddedStrings <- embed(fieldStrings, index.encoders)
    } yield {
      val all = new util.ArrayList[LuceneDocument]()
      val ids = new ArrayBuffer[String]()
      docs.foreach(doc => {
        val mapped = doc.cast(index.mapping)
        val buffer = new LuceneDocument()
        mapped.fields.foreach {
          case field @ TextField(name, value) =>
            index.mapping.textFields.get(name) match {
              case None => logger.warn(s"text field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) =>
                if (name == "_id") ids.addOne(value)
                mapping match {
                  case TextFieldSchema(_, tpe: SemanticSearchLikeType, _, _, _, _, _, _) =>
                    textFieldWriter.write(field, mapping, buffer, embeddedStrings.getOrElse(tpe, Map.empty))
                  case _ => textFieldWriter.write(field, mapping, buffer, Map.empty)
                }

            }
          case field @ TextListField(name, value) =>
            index.mapping.textListFields.get(name) match {
              case None => logger.warn(s"text[] field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => textListFieldWriter.write(field, mapping, buffer, Map.empty)
            }
          case field @ IntField(name, value) =>
            index.mapping.intFields.get(name) match {
              case None => logger.warn(s"int field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => intFieldWriter.write(field, mapping, buffer)
            }
          case field @ LongField(name, value) =>
            index.mapping.longFields.get(name) match {
              case None => logger.warn(s"long field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => longFieldWriter.write(field, mapping, buffer)
            }
          case field @ FloatField(name, value) =>
            index.mapping.floatFields.get(name) match {
              case None => logger.warn(s"float field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => floatFieldWriter.write(field, mapping, buffer)
            }
          case field @ DoubleField(name, value) =>
            index.mapping.doubleFields.get(name) match {
              case None => logger.warn(s"double field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => doubleFieldWriter.write(field, mapping, buffer)
            }
          case field @ BooleanField(name, value) =>
            index.mapping.booleanFields.get(name) match {
              case None =>
                logger.warn(s"boolean field '$name' is not defined in mapping of index '${index.name.value}'")
              case Some(mapping) => boolFieldWriter.write(field, mapping, buffer)
            }
        }
        all.add(buffer)
      })
      val deleteIds = ids.map(id => new Term("_id_raw", id))
      writer.deleteDocuments(deleteIds.toSeq*)
      writer.addDocuments(all)
    }
  }
  
  def strings(mapping: IndexMapping, docs: List[Document]): Map[SemanticSearchLikeType, List[String]] = {
    val candidates = for {
      doc   <- docs
      field <- doc.fields
      model <- mapping.fields.get(field.name).toList.flatMap {
        case TextLikeFieldSchema(name, tpe: SemanticSearchLikeType, _, _, _, _, _, _) =>
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
      encoders: EmbedderDict
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

  def flush(): IO[Boolean] = {
    IO(writer.numRamDocs()).flatMap {
      case 0 => debug(s"skipping flush of '${index.name.value}', no uncommitted changes") *> IO(false)
      case other =>
        debug(s"mem docs: $other") *> IO(writer.commit()).flatMap {
          case -1 => debug(s"nothing to commit for index '${index.name}'") *> IO.pure(false)
          case seqnum =>
            for {
              _        <- info(s"index '${index.name.value}' commit, seqnum=$seqnum")
              manifest <- index.master.createManifest(index.mapping, seqnum)
              _        <- info(s"generated manifest for files ${manifest.files.map(_.name).sorted}")
              _        <- index.master.writeManifest(manifest)
            } yield {
              true
            }
        }
    }
  }

}

object Indexer extends Logging {
  def open(index: Index): Resource[IO, Indexer] = {
    for {
      writer <- indexWriter(index.directory, index.mapping)
      niw    <- Resource.make(IO(Indexer(index, writer)))(i => i.flush().void)
    } yield {
      niw
    }
  }

  def indexWriter(directory: Directory, mapping: IndexMapping): Resource[IO, IndexWriter] = for {
    analyzer <- Resource.eval(IO(IndexMapping.createAnalyzer(mapping)))
    writer   <- Indexer.indexWriter(directory, analyzer)
  } yield {
    writer
  }

  def indexWriter(directory: Directory, analyzer: Analyzer): Resource[IO, IndexWriter] =
    for {
      codec  <- Resource.pure(NixiesearchCodec())
      config <- Resource.eval(IO(new IndexWriterConfig(analyzer).setCodec(codec)))
      _      <- Resource.eval(debug("opening IndexWriter"))
      writer <- Resource.make(IO(new IndexWriter(directory, config)))(w => IO(w.close()) *> debug("IndexWriter closed"))
    } yield {
      writer
    }

}
