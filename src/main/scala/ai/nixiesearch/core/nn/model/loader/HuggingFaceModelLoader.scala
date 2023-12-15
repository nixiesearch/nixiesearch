package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.loader.ModelLoader.TransformersConfig
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache, OnnxSession}
import cats.effect.IO
import io.circe.parser.*

import java.io.{ByteArrayInputStream, File}

object HuggingFaceModelLoader extends Logging with ModelLoader[HuggingFaceHandle] {
  override def load(handle: HuggingFaceHandle): IO[OnnxSession] =
    for {
      cache <- ModelFileCache.create()
      sbert <- HuggingFaceClient
        .create(cache)
        .use(hf =>
          for {
            card      <- hf.model(handle)
            modelFile <- chooseModelFile(card.siblings.map(_.rfilename))
            tokenizerFile <- IO.fromOption(card.siblings.map(_.rfilename).find(_ == "tokenizer.json"))(
              BackendError("Cannot find tokenizer.json in repo")
            )
            _          <- info(s"Fetching $handle from HF: model=$modelFile tokenizer=$tokenizerFile")
            modelBytes <- hf.getCached(handle, modelFile)
            vocabBytes <- hf.getCached(handle, tokenizerFile)
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
