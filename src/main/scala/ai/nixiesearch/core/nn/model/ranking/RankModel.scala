package ai.nixiesearch.core.nn.model.ranking

import cats.effect.IO
import fs2.{Chunk, Stream}

sealed trait RankModel {
  def model: String
  def provider: String
  def batchSize: Int

  def encode(query: String, docs: List[String]): IO[List[Float]] = Stream
    .emits(docs)
    .chunkN(batchSize)
    .evalMap(batch => encodeBatch(query, batch.toList).map(batch => Chunk.from(batch)))
    .unchunks
    .compile
    .toList

  def encodeBatch(query: String, docs: List[String]): IO[List[Float]]
}
