package ai.nixiesearch.core.nn.model.ranking

import ai.nixiesearch.core.nn.ModelRef
import cats.effect.IO
import fs2.{Chunk, Stream}

trait RankModel {
  def model: String
  def provider: String
  def batchSize: Int

  def score(query: String, docs: List[String]): IO[List[Float]] = Stream
    .emits(docs)
    .chunkN(batchSize)
    .evalMap(batch => scoreBatch(query, batch.toList).map(batch => Chunk.from(batch)))
    .unchunks
    .compile
    .toList

  def scoreBatch(query: String, docs: List[String]): IO[List[Float]]
}

object RankModel {}
