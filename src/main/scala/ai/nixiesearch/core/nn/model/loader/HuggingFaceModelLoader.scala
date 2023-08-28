package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.loader.ModelLoader.TransformersConfig
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache, OnnxSession}
import cats.effect.IO
import io.circe.parser.*

import java.io.{ByteArrayInputStream, File}

object HuggingFaceModelLoader extends Logging with ModelLoader[HuggingFaceHandle] {
  override def load(handle: HuggingFaceHandle, modelFile: String = MODEL_FILE): IO[OnnxSession] =
    for {
      cache <- ModelFileCache.create()
      sbert <- HuggingFaceClient
        .create(cache)
        .use(hf =>
          for {
            modelBytes <- hf.getCached(handle, modelFile)
            vocabBytes <- hf.getCached(handle, VOCAB_FILE)
            config <- hf
              .getCached(handle, CONFIG_FILE)
              .flatMap(bytes => IO.fromEither(decode[TransformersConfig](new String(bytes))))
            session <- OnnxSession.load(
              model = new ByteArrayInputStream(modelBytes),
              dic = new ByteArrayInputStream(vocabBytes),
              dim = config.hidden_size
            )
          } yield {
            session
          }
        )
    } yield {
      sbert
    }

}
