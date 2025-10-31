package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.*

trait SearchTest extends AnyFlatSpec {
  def inference: InferenceConfig = TestInferenceConfig.empty()
  def mapping: IndexMapping
  def docs: List[Document]

  given documentCodec: Codec[Document]                 = ???//Document.codecFor(mapping)
  given searchResponseEncoder: Encoder[SearchResponse] = deriveEncoder[SearchResponse].mapJson(_.dropNullValues)
  given searchResponseDecoder: Decoder[SearchResponse] = deriveDecoder
  given searchResponseEncJson: EntityEncoder[IO, SearchResponse] = jsonEncoderOf
  given searchResponseDecJson: EntityDecoder[IO, SearchResponse] = jsonOf

  def withIndex(code: LocalNixie => Any): Unit = {
    val (cluster, shutdown) = LocalNixie.create(mapping, inference).allocated.unsafeRunSync()
    try {
      if (docs.nonEmpty) {
        cluster.indexer.addDocuments(docs).unsafeRunSync()
        cluster.indexer.flush().unsafeRunSync()
        cluster.indexer.index.sync().unsafeRunSync()
        cluster.searcher.sync().unsafeRunSync()
      }
      code(cluster)
    } finally {
      shutdown.unsafeRunSync()
    }
  }

}
