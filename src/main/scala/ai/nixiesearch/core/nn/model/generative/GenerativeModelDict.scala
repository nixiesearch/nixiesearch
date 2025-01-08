package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.LlamacppInferenceModelConfig
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.generative.GenerativeModel.LlamacppGenerativeModel
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.util.GPUUtils
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.io.file.Path as Fs2Path
import fs2.Stream

case class GenerativeModelDict(models: Map[ModelRef, GenerativeModel]) {
  def generate(name: ModelRef, input: String, maxTokens: Int): Stream[IO, String] = models.get(name) match {
    case Some(model) => model.generate(input, maxTokens)
    case None =>
      Stream.raiseError(
        UserError(s"RAG model handle ${name} cannot be found among these found in config: ${models.keys.toList}")
      )
  }
}

object GenerativeModelDict extends Logging {

  def create(
      models: Map[ModelRef, CompletionInferenceModelConfig],
      cache: ModelFileCache
  ): Resource[IO, GenerativeModelDict] =
    for {
      generativeModels <- models.toList.map {
        case (name: ModelRef, conf @ LlamacppInferenceModelConfig(handle: HuggingFaceHandle, _, _, _)) =>
          createHuggingface(handle, name, conf, cache).map(model => name -> model)
        case (name: ModelRef, conf @ LlamacppInferenceModelConfig(handle: LocalModelHandle, _, _, _)) =>
          createLocal(handle, name, conf).map(model => name -> model)
      }.sequence
    } yield {
      GenerativeModelDict(generativeModels.toMap)
    }

  def createHuggingface(
      handle: HuggingFaceHandle,
      name: ModelRef,
      config: LlamacppInferenceModelConfig,
      cache: ModelFileCache
  ): Resource[IO, GenerativeModel] = for {
    hf <- HuggingFaceClient.create(cache)
    modelFile <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- chooseModelFile(card.siblings.map(_.rfilename), config.file)
      _         <- info(s"Fetching $handle from HF: model=$modelFile")
      modelPath <- hf.getCached(handle, modelFile)
    } yield {
      modelPath
    })
    isGPU <- Resource.eval(IO(GPUUtils.isGPUBuild()))
    genModel <- LlamacppGenerativeModel.create(
      path = modelFile,
      options = config.options,
      useGpu = isGPU,
      name = name
    )
  } yield {
    genModel
  }

  def createLocal(
      handle: LocalModelHandle,
      name: ModelRef,
      config: LlamacppInferenceModelConfig
  ): Resource[IO, GenerativeModel] = {
    for {
      modelFile <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- chooseModelFile(files, config.file)
        _         <- info(s"loading $modelFile from $handle")
      } yield {
        path.toNioPath.resolve(modelFile)
      })
      isGPU <- Resource.eval(IO(GPUUtils.isGPUBuild()))
      genModel <- LlamacppGenerativeModel.create(
        path = modelFile,
        options = config.options,
        useGpu = isGPU,
        name = name
      )
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
