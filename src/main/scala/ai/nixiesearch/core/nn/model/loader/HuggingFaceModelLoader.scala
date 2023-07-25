package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelCache, OnnxSession}
import cats.effect.IO

import java.io.{ByteArrayInputStream, File}

object HuggingFaceModelLoader extends Logging {
  implicit val huggingFaceModelLoader: ModelLoader[HuggingFaceHandle] = new ModelLoader[HuggingFaceHandle] {
    override def load(handle: HuggingFaceHandle, dim: Int, modelFile: String, vocabFile: String): IO[OnnxSession] =
      for {
        cache        <- ModelCache.create()
        modelDirName <- IO(handle.asList.mkString(File.separator))
        sbert <- HuggingFaceClient
          .create()
          .use(hf =>
            for {
              modelBytes <- cache.getIfExists(modelDirName, modelFile).flatMap {
                case Some(bytes) => info(s"found $modelFile in cache") *> IO.pure(bytes)
                case None => hf.modelFile(handle, modelFile).flatTap(bytes => cache.put(modelDirName, modelFile, bytes))
              }
              vocabBytes <- cache.getIfExists(modelDirName, vocabFile).flatMap {
                case Some(bytes) => info(s"found $vocabFile in cache") *> IO.pure(bytes)
                case None => hf.modelFile(handle, vocabFile).flatTap(bytes => cache.put(modelDirName, vocabFile, bytes))
              }
              session <- OnnxSession.load(
                model = new ByteArrayInputStream(modelBytes),
                dic = new ByteArrayInputStream(vocabBytes),
                dim = dim
              )
            } yield {
              session
            }
          )
      } yield {
        sbert
      }

  }
}
