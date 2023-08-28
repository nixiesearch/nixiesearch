package ai.nixiesearch.core.nn.model

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.{Config, FieldSchema}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*

case class BiEncoderCache(encoders: Map[ModelHandle, OnnxBiEncoder]) {
  def get(handle: ModelHandle) = encoders.get(handle)
}

object BiEncoderCache extends Logging {
  def create(mapping: IndexMapping): Resource[IO, BiEncoderCache] = for {
    searchModels <- Resource.eval(IO(mapping.fields.toList.map(_._2).collect {
      case FieldSchema.TextFieldSchema(_, SemanticSearch(model, _), _, _, _, _)     => model
      case FieldSchema.TextListFieldSchema(_, SemanticSearch(model, _), _, _, _, _) => model
    }))
    _ <- Resource.eval(info(s"Loading ONNX models: $searchModels"))
    encoders <- Resource.make(
      searchModels.traverse(h => OnnxSession.load(h).map(s => h -> OnnxBiEncoder(s))).map(_.toMap)
    )(_.toList.map(_._2).traverse(bi => IO(bi.close())).void)
  } yield {
    BiEncoderCache(encoders)
  }
}
