package ai.nixiesearch.index

import ai.nixiesearch.api.IndexModifyRoute.DeleteResponse
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.{IndexConfig, IndexMapping}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.*
import ai.nixiesearch.core.codec.compat.Nixiesearch101Codec
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.field.TextField.FILTER_SUFFIX
import ai.nixiesearch.core.metrics.{IndexerMetrics, Metrics}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.util.DocumentEmbedder
import cats.effect.{IO, Resource}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.store.Directory
import org.apache.lucene.document.Document as LuceneDocument

import java.util
import cats.syntax.all.*
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.apache.lucene.search.MatchAllDocsQuery

import language.experimental.namedTuples
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class Indexer(index: Index, writer: IndexWriter, metrics: Metrics) extends Logging {
  lazy val docEmbedder = DocumentEmbedder(index.mapping, index.models.embedding)

  def addDocuments(docs: List[Document]): IO[Unit] = {
    for {
      _            <- debug(s"adding ${docs.size} docs to index '${index.name.value}'")
      embeddedDocs <- docEmbedder.embed(docs)
    } yield {
      val all = new util.ArrayList[LuceneDocument]()
      val ids = new ArrayBuffer[String]()
      embeddedDocs.foreach(doc => {
        val buffer = new LuceneDocument()
        doc.fields.foreach {
          case field @ TextField(name, value, _) =>
            if (name == "_id") ids.addOne(value)
            writeField(
              field,
              TextField,
              index.mapping.fieldSchemaOf[TextFieldSchema](field.name),
              buffer
            )

          case field @ TextListField(name, value, _) =>
            writeField(
              field,
              TextListField,
              index.mapping.fieldSchemaOf[TextListFieldSchema](field.name),
              buffer
            )
          case field @ IntField(name, value) =>
            writeField(field, IntField, index.mapping.fieldSchemaOf[IntFieldSchema](field.name), buffer)
          case field @ IntListField(name, value) =>
            writeField(field, IntListField, index.mapping.fieldSchemaOf[IntListFieldSchema](field.name), buffer)
          case field @ LongField(name, value) =>
            writeField(field, LongField, index.mapping.fieldSchemaOf[LongFieldSchema](field.name), buffer)
          case field @ LongListField(name, value) =>
            writeField(field, LongListField, index.mapping.fieldSchemaOf[LongListFieldSchema](field.name), buffer)
          case field @ FloatField(name, value) =>
            writeField(field, FloatField, index.mapping.fieldSchemaOf[FloatFieldSchema](field.name), buffer)
          case field @ DoubleField(name, value) =>
            writeField(field, DoubleField, index.mapping.fieldSchemaOf[DoubleFieldSchema](field.name), buffer)
          case field @ BooleanField(name, value) =>
            writeField(field, BooleanField, index.mapping.fieldSchemaOf[BooleanFieldSchema](field.name), buffer)
          case field @ GeopointField(name, lat, lon) =>
            writeField(field, GeopointField, index.mapping.fieldSchemaOf[GeopointFieldSchema](field.name), buffer)
          case field @ DateField(name, value) =>
            writeField(field, DateField, index.mapping.fieldSchemaOf[DateFieldSchema](field.name), buffer)
          case field @ DateTimeField(name, value) =>
            writeField(field, DateTimeField, index.mapping.fieldSchemaOf[DateTimeFieldSchema](field.name), buffer)
        }
        all.add(buffer)
      })
      val deleteIds = ids.map(id => new Term("_id" + FILTER_SUFFIX, id))
      writer.deleteDocuments(deleteIds.toSeq*)
      writer.addDocuments(all)
    }
  }

  private def writeField[T <: Field, S <: FieldSchema[T]](
      field: T,
      codec: FieldCodec[T, S, ?],
      mapping: Option[S],
      buffer: LuceneDocument
  ): Unit = mapping match {
    case None          => logger.warn(s"field '${field.name}' is not defined in index mapping for ${index.name.value}")
    case Some(mapping) => codec.writeLucene(field, mapping, buffer)
  }

  def flush(): IO[Boolean] = {
    IO((writer.numRamDocs() > 0) || writer.hasDeletions || writer.hasUncommittedChanges).flatMap {
      case false => debug(s"skipping flush of '${index.name.value}', no uncommitted changes") *> IO(false)
      case true  =>
        for {
          _ <- debug(
            s"memdocs=${writer.numRamDocs()} deletes=${writer.hasDeletions} uncommitted=${writer.hasUncommittedChanges}"
          )
          _      <- IO(metrics.indexer.flushTotal.labelValues(index.name.value).inc())
          start  <- IO(System.currentTimeMillis())
          seqnum <- IO(writer.commit())
          _      <- IO(
            metrics.indexer.flushTimeSeconds
              .labelValues(index.name.value)
              .inc((System.currentTimeMillis() - start) / 1000.0)
          )
          result <- seqnum match {
            case -1        => debug(s"nothing to commit for index '${index.name}'") *> IO.pure(false)
            case posSeqNum =>
              for {
                _        <- info(s"index '${index.name.value}' commit, seqnum=$posSeqNum")
                manifest <- index.master.createManifest(index.mapping, posSeqNum)
                _        <- info(s"generated manifest for files ${manifest.files.map(_.name).sorted}")
                _        <- index.master.writeManifest(manifest)
              } yield {
                true
              }

          }
        } yield {
          result
        }

    }
  }

  def merge(segments: Int): IO[Unit] = {
    for {
      _ <- info(s"Forced segment merge started, segments=$segments")
      _ <- IO(writer.forceMerge(segments, true))
      _ <- info("Forced merge finished")
      _ <- IO(writer.commit()).flatMap {
        case -1     => debug(s"nothing to commit for index '${index.name}'") *> IO.pure(false)
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

  def delete(docid: String): IO[Int] = for {
    before <- IO(writer.getDocStats)
    _      <- IO(writer.deleteDocuments(new Term("_id" + FILTER_SUFFIX, docid)))
    after  <- IO(writer.getDocStats)
  } yield {
    before.numDocs - after.numDocs
  }

  def delete(filters: Option[Filters]): IO[Int] = for {
    query <- filters match {
      case None    => IO.pure(new MatchAllDocsQuery())
      case Some(f) =>
        f.toLuceneQuery(index.mapping).map {
          case Some(value) => value
          case None        => new MatchAllDocsQuery()
        }
    }
    before <- IO(writer.getDocStats)
    _      <- IO(writer.deleteDocuments(query))
    after  <- IO(writer.getDocStats)
  } yield {
    before.numDocs - after.numDocs
  }

}

object Indexer extends Logging {
  def open(index: Index, metrics: Metrics): Resource[IO, Indexer] = {
    for {
      writer <- indexWriter(index.directory, index.mapping)
      niw    <- Resource.make(IO(Indexer(index, writer, metrics)))(i => i.flush().void)
    } yield {
      niw
    }
  }

  def indexWriter(directory: Directory, mapping: IndexMapping): Resource[IO, IndexWriter] =
    for {
      codec  <- Resource.pure(Nixiesearch101Codec(mapping))
      config <- Resource.eval(
        IO(
          new IndexWriterConfig(mapping.analyzer)
            .setCodec(codec)
            .setRAMBufferSizeMB(mapping.config.indexer.ramBufferSize.mb.toDouble)
        )
      )
      _      <- Resource.eval(debug("opening IndexWriter"))
      writer <- Resource.make(IO(new IndexWriter(directory, config)))(w => IO(w.close()) *> debug("IndexWriter closed"))
    } yield {
      writer
    }

}
