package ai.nixiesearch.core.nn.model.embedding.providers
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import cats.effect.IO
import fs2.{Chunk, Stream}

trait EmbedModelProvider extends EmbedModel {

  def encode(task: TaskType, docs: List[String]): Stream[IO, Array[Float]] = Stream
    .emits(docs)
    .chunkN(batchSize)
    .evalMap(batch => encodeBatch(task, batch.toList).map(batch => Chunk.array(batch)))
    .unchunks

  protected def encodeBatch(task: TaskType, batch: List[String]): IO[Array[Array[Float]]]

}
