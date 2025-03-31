package ai.nixiesearch.core.nn.model.embedding.cache

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.cache.CachedEmbedModel.CacheRecord.{EmbedRecord, TextRecord}
import ai.nixiesearch.core.nn.model.embedding.cache.CachedEmbedModel.ModelId
import cats.effect.IO
import fs2.{Chunk, Pull, Stream}

import scala.collection.mutable.ArrayBuffer

trait CachedEmbedModel extends EmbedModel with Logging {
  def underlying: EmbedModel
  override val model: String    = underlying.model
  override val provider: String = underlying.provider

  override def encode(task: TaskType, docs: List[String]): Stream[IO, Array[Float]] = {
    def splitCached(
        docs: List[String],
        response: List[Option[Array[Float]]]
    ): IO[(List[EmbedRecord], List[TextRecord])] = IO {
      val cached    = ArrayBuffer[EmbedRecord]()
      val nonCached = ArrayBuffer[TextRecord]()
      docs.zip(response).zipWithIndex.foreach {
        case ((str, Some(embed)), index) =>
          cached.addOne(EmbedRecord(index, str, embed))
        case ((str, None), index) =>
          nonCached.addOne(TextRecord(index, str))
      }
      (cached.toList, nonCached.toList)
    }

    def mergeEmbeds(embeds: List[Array[Float]], targets: List[TextRecord]): List[EmbedRecord] =
      embeds.zip(targets).map { case (embed, TextRecord(index, text)) =>
        EmbedRecord(index, text, embed)
      }

    Stream
      .emits(docs)
      .chunkN(batchSize)
      .evalMap(chunk =>
        for {
          docs                <- IO(chunk.toList)
          response            <- get(task, docs)
          (cached, nonCached) <- splitCached(docs, response)
          embeds <- underlying.encode(task, nonCached.map(_.text)).compile.toList.map(mergeEmbeds(_, nonCached))
          _      <- put(task, embeds)
        } yield {
          val result = new Array[Array[Float]](chunk.size)
          cached.foreach(ce => result(ce.index) = ce.embed)
          embeds.foreach(ce => result(ce.index) = ce.embed)
          Chunk.array(result)
        }
      )
      .unchunks
  }

  def get(task: TaskType, docs: List[String]): IO[List[Option[Array[Float]]]]

  def put(task: TaskType, docs: List[EmbedRecord]): IO[Unit]

}

object CachedEmbedModel {
  sealed trait CacheRecord {
    def index: Int
  }
  object CacheRecord {
    case class EmbedRecord(index: Int, text: String, embed: Array[Float]) extends CacheRecord
    case class TextRecord(index: Int, text: String)                       extends CacheRecord
  }

  case class ModelId(provider: String, model: String)
}
