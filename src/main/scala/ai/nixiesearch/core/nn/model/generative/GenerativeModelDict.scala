package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.mapping.RAGConfig.RAGModelConfig
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.generative.GenerativeModel.LlamacppGenerativeModel
import ai.nixiesearch.core.nn.model.generative.GenerativeModelDict.ModelId
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.io.file.Path as Fs2Path
import fs2.Stream

case class GenerativeModelDict(models: Map[ModelId, GenerativeModel]) {
  def generate(name: ModelId, input: String, maxTokens: Int): Stream[IO, String] = models.get(name) match {
    case Some(model) => model.generate(input, maxTokens)
    case None =>
      Stream.raiseError(
        UserError(s"RAG model handle ${name} cannot be found among these found in config: ${models.keys.toList}")
      )
  }
}

object GenerativeModelDict extends Logging {
  case class ModelId(value: String)

  def create(models: List[RAGModelConfig], cache: ModelFileCache): Resource[IO, GenerativeModelDict] = for {
    generativeModels <- models.map {
      case conf @ RAGModelConfig(handle: HuggingFaceHandle, _, _, _) =>
        createHuggingface(handle, conf, cache).map(model => ModelId(conf.name) -> model)
      case conf @ RAGModelConfig(handle: LocalModelHandle, _, _, _) =>
        createLocal(handle, conf).map(model => ModelId(conf.name) -> model)
    }.sequence
  } yield {
    GenerativeModelDict(generativeModels.toMap)
  }

  def createHuggingface(
      handle: HuggingFaceHandle,
      config: RAGModelConfig,
      cache: ModelFileCache
  ): Resource[IO, GenerativeModel] = for {
    hf <- HuggingFaceClient.create(cache)
    modelFile <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- chooseModelFile(card.siblings.map(_.rfilename), handle.file)
      _         <- info(s"Fetching $handle from HF: model=$modelFile")
      modelPath <- hf.getCached(handle, modelFile)
    } yield {
      modelPath
    })
    genModel <- LlamacppGenerativeModel.create(modelFile, config.prompt)
  } yield {
    genModel
  }

  def createLocal(handle: LocalModelHandle, config: RAGModelConfig): Resource[IO, GenerativeModel] = {
    for {
      modelFile <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- chooseModelFile(files, handle.file)
        _         <- info(s"loading $modelFile from $handle")
      } yield {
        path.toNioPath.resolve(modelFile)
      })
      genModel <- LlamacppGenerativeModel.create(modelFile, config.prompt)
    } yield {
      genModel
    }
  }

  def chooseModelFile(files: List[String], forced: Option[String]): IO[String] = forced match {
    case Some(file) => IO.pure(file)
    case None =>
      files.find(f => f.toLowerCase().endsWith("gguf")) match {
        case Some(file) => IO.pure(file)
        case None       => IO.raiseError(UserError(s"cannot choose a GGUF model file out of this list: ${files}"))
      }
  }

}
