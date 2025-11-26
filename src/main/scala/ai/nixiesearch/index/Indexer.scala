package ai.nixiesearch.index

import ai.nixiesearch.api.IndexModifyRoute.DeleteResponse
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexConfig, IndexMapping}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.codec.*
import ai.nixiesearch.core.field.FieldCodec.FILTER_SUFFIX
import ai.nixiesearch.core.codec.compat.{Nixiesearch101Codec, Nixiesearch103Codec}
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.metrics.{IndexerMetrics, Metrics}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.util.DocumentEmbedder
import cats.effect.{IO, Resource}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, NoMergePolicy, Term}
import org.apache.lucene.store.Directory
import org.apache.lucene.document.Document as LuceneDocument

import java.util
import cats.syntax.all.*
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.apache.lucene.search.MatchAllDocsQuery

import java.util.UUID
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
        val id = doc.fields
          .collectFirst { case IdField("_id", id) => id }
          .getOrElse(UUID.randomUUID().toString)
        ids.append(id)
        val docGroup = DocumentGroup(id)
        doc.fields.foreach {
          case field @ IdField(_, _) => // handled by DocumentGroup

          case field @ TextField(name, value, _) =>
            writeField(
              field,
              index.mapping.fieldSchema[TextFieldSchema](StringName(field.name)),
              docGroup
            )

          case field @ TextListField(name, value, _) =>
            writeField(
              field,
              index.mapping.fieldSchema[TextListFieldSchema](StringName(field.name)),
              docGroup
            )
          case field @ IntField(name, value) =>
            writeField(field, index.mapping.fieldSchema[IntFieldSchema](StringName(field.name)), docGroup)
          case field @ IntListField(name, value) =>
            writeField(field, index.mapping.fieldSchema[IntListFieldSchema](StringName(field.name)), docGroup)
          case field @ LongField(name, value) =>
            writeField(field, index.mapping.fieldSchema[LongFieldSchema](StringName(field.name)), docGroup)
          case field @ LongListField(name, value) =>
            writeField(field, index.mapping.fieldSchema[LongListFieldSchema](StringName(field.name)), docGroup)
          case field @ FloatField(name, value) =>
            writeField(field, index.mapping.fieldSchema[FloatFieldSchema](StringName(field.name)), docGroup)
          case field @ FloatListField(name, value) =>
            writeField(field, index.mapping.fieldSchema[FloatListFieldSchema](StringName(field.name)), docGroup)
          case field @ DoubleField(name, value) =>
            writeField(field, index.mapping.fieldSchema[DoubleFieldSchema](StringName(field.name)), docGroup)
          case field @ DoubleListField(name, value) =>
            writeField(field, index.mapping.fieldSchema[DoubleListFieldSchema](StringName(field.name)), docGroup)
          case field @ BooleanField(name, value) =>
            writeField(field, index.mapping.fieldSchema[BooleanFieldSchema](StringName(field.name)), docGroup)
          case field @ GeopointField(name, lat, lon) =>
            writeField(field, index.mapping.fieldSchema[GeopointFieldSchema](StringName(field.name)), docGroup)
          case field @ DateField(name, value) =>
            writeField(field, index.mapping.fieldSchema[DateFieldSchema](StringName(field.name)), docGroup)
          case field @ DateTimeField(name, value) =>
            writeField(field, index.mapping.fieldSchema[DateTimeFieldSchema](StringName(field.name)), docGroup)
        }
        docGroup.toLuceneDocuments().foreach(doc => all.add(doc))
      })
      val deleteIds = ids.map(id => new Term("_id" + FILTER_SUFFIX, id))
      writer.deleteDocuments(deleteIds.toSeq*)
      writer.addDocuments(all)
    }
  }

  private def writeField[T <: Field, S <: FieldSchema[T]](
      field: T,
      mapping: Option[S],
      buffer: DocumentGroup
  ): Unit = mapping match {
    case None => logger.warn(s"Field '${field.name}' is not defined in the index mapping for '${index.name.value}'.")
    case Some(mapping) => mapping.codec.writeLucene(field, buffer)
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
    _      <- IO(writer.deleteDocuments(new Term("_id" + FieldCodec.FILTER_SUFFIX, docid)))
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
      codec  <- Resource.pure(Nixiesearch103Codec(mapping))
      config <- Resource.eval(
        IO {
          val config = new IndexWriterConfig(mapping.analyzer)
            .setCodec(codec)
            .setRAMBufferSizeMB(mapping.config.indexer.ram_buffer_size.mb.toDouble)
          mapping.config.indexer.merge_policy match {
            case None =>
              logger.debug("Using default Lucene merge policy")
              config
            case Some(mergePolicy) =>
              logger.debug(s"Using merge policy $mergePolicy")
              config.setMergePolicy(mergePolicy.toLuceneMergePolicy())
          }

        }
      )
      _      <- Resource.eval(debug("opening IndexWriter"))
      writer <- Resource.make(IO(new IndexWriter(directory, config)))(w => IO(w.close()) *> debug("IndexWriter closed"))
    } yield {
      writer
    }

}
