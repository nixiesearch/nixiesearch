package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.{
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextLikeFieldSchema
}
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.{IndexConfig, IndexMapping}
import ai.nixiesearch.config.mapping.SearchType.SemanticSearchLikeType
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.{
  BooleanFieldCodec,
  DoubleFieldCodec,
  FieldCodec,
  FloatFieldCodec,
  GeopointFieldCodec,
  IntFieldCodec,
  LongFieldCodec,
  NixiesearchCodec,
  TextFieldCodec,
  TextListFieldCodec
}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.index.sync.Index
import cats.effect.{IO, Resource}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.store.Directory
import org.apache.lucene.document.Document as LuceneDocument

import java.util
import cats.implicits.*

import scala.collection.mutable.ArrayBuffer

case class Indexer(index: Index, writer: IndexWriter) extends Logging {

  def addDocuments(docs: List[Document]): IO[Unit] = {
    for {
      _               <- debug(s"adding ${docs.size} docs to index '${index.name.value}'")
      fieldStrings    <- IO(strings(index.mapping, docs))
      embeddedStrings <- embed(fieldStrings, index.models.embedding)
    } yield {
      val all = new util.ArrayList[LuceneDocument]()
      val ids = new ArrayBuffer[String]()
      docs.foreach(doc => {
        val mapped = doc.cast(index.mapping)
        val buffer = new LuceneDocument()
        mapped.fields.foreach {
          case field @ TextField(name, value) =>
            if (name == "_id") ids.addOne(value)
            writeField(field, TextFieldCodec, index.mapping.textFields, buffer, fieldEmbeds(field, embeddedStrings))
          case field @ TextListField(name, value) =>
            writeField(
              field,
              TextListFieldCodec,
              index.mapping.textListFields,
              buffer,
              fieldEmbeds(field, embeddedStrings)
            )
          case field @ IntField(name, value) =>
            writeField(field, IntFieldCodec, index.mapping.intFields, buffer)
          case field @ LongField(name, value) =>
            writeField(field, LongFieldCodec, index.mapping.longFields, buffer)
          case field @ FloatField(name, value) =>
            writeField(field, FloatFieldCodec, index.mapping.floatFields, buffer)
          case field @ DoubleField(name, value) =>
            writeField(field, DoubleFieldCodec, index.mapping.doubleFields, buffer)
          case field @ BooleanField(name, value) =>
            writeField(field, BooleanFieldCodec, index.mapping.booleanFields, buffer)
          case field @ GeopointField(name, lat, lon) =>
            writeField(field, GeopointFieldCodec, index.mapping.geopointFields, buffer)
        }
        all.add(buffer)
      })
      val deleteIds = ids.map(id => new Term("_id_raw", id))
      writer.deleteDocuments(deleteIds.toSeq*)
      writer.addDocuments(all)
    }
  }

  private def writeField[T <: Field, S <: FieldSchema[T]](
      field: T,
      codec: FieldCodec[T, S, ?],
      mappings: Map[String, S],
      buffer: LuceneDocument,
      embeds: Map[String, Array[Float]] = Map.empty
  ): Unit = mappings.get(field.name) match {
    case None          => logger.warn(s"field '${field.name}' is not defined in index mapping for ${index.name.value}")
    case Some(mapping) => codec.write(field, mapping, buffer, embeds)
  }

  private def fieldEmbeds[T <: TextLikeField](
      field: T,
      allFieldEmbeds: Map[ModelRef, Map[String, Array[Float]]]
  ): Map[String, Array[Float]] = field match {
    case t: TextLikeField =>
      index.mapping.textLikeFields.get(t.name) match {
        case Some(TextLikeFieldSchema(_, tpe: SemanticSearchLikeType, _, _, _, _, _, _)) =>
          allFieldEmbeds.getOrElse(tpe.model, Map.empty)
        case _ => Map.empty
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
      encoders: EmbedModelDict
  ): IO[Map[ModelRef, Map[String, Array[Float]]]] = {
    targets.toList
      .traverse { case (field, strings) =>
        for {
          encoded <- strings.distinct
            .sortBy(-_.length)
            .grouped(8)
            .toList
            .flatTraverse(batch =>
              encoders
                .encodeDocuments(field.model, batch)
                .flatMap(embeddings => IO(batch.zip(embeddings)))
            )
        } yield {
          field.model -> encoded.toMap
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

  def merge(segments: Int): IO[Unit] = {
    for {
      _ <- info(s"Forced segment merge started, segments=$segments")
      _ <- IO(writer.forceMerge(segments, true))
      _ <- info("Forced merge finished")
      _ <- IO(writer.commit()).flatMap {
        case -1 => debug(s"nothing to commit for index '${index.name}'") *> IO.pure(false)
        case seqnum =>
          for {
            _        <- info(s"index '${index.name.value}' commit, seqnum=$seqnum")
            manifest <- index.master.createManifest(index.mapping, seqnum)
            _        <- info(s"generated manifest for files ${manifest.files.map(_.name).sorted}")
            _        <- index.master.writeManifest(manifest)
          } yield {}
      }
    } yield {}
  }

}

object Indexer extends Logging {
  def open(index: Index): Resource[IO, Indexer] = {
    for {
      writer <- indexWriter(index.directory, index.mapping, index.mapping.config)
      niw    <- Resource.make(IO(Indexer(index, writer)))(i => i.flush().void)
    } yield {
      niw
    }
  }

  def indexWriter(directory: Directory, mapping: IndexMapping, config: IndexConfig): Resource[IO, IndexWriter] = for {
    analyzer <- Resource.eval(IO(IndexMapping.createAnalyzer(mapping)))
    writer   <- Indexer.indexWriter(directory, analyzer, config)
  } yield {
    writer
  }

  def indexWriter(directory: Directory, analyzer: Analyzer, config: IndexConfig): Resource[IO, IndexWriter] =
    for {
      codec  <- Resource.pure(NixiesearchCodec(config))
      config <- Resource.eval(IO(new IndexWriterConfig(analyzer).setCodec(codec)))
      _      <- Resource.eval(debug("opening IndexWriter"))
      writer <- Resource.make(IO(new IndexWriter(directory, config)))(w => IO(w.close()) *> debug("IndexWriter closed"))
    } yield {
      writer
    }

}
