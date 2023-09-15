package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.LocalModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import ai.nixiesearch.core.nn.model.loader.ModelLoader.TransformersConfig
import cats.effect.IO
import fs2.io.file.{Files, Path}
import org.apache.commons.io.IOUtils
import io.circe.parser.*

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.charset.StandardCharsets

object LocalModelLoader extends Logging with ModelLoader[LocalModelHandle] {
  override def load(handle: LocalModelHandle): IO[OnnxSession] =
    for {
      files      <- Files[IO].list(Path(handle.dir)).map(_.fileName.toString).compile.toList
      modelFile  <- IO.fromOption(files.find(_.endsWith(".onnx")))(new Exception("cannot find *.onnx file in dir"))
      tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(new Exception("cannot find tokenizer.json file in dir"))
      _          <- info(s"loading $modelFile from $handle")
      modelBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + modelFile))))
      vocabBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + tokenizerFile))))
      configBytes <- IO(
        IOUtils.toString(
          new FileInputStream(new File(handle.dir + File.separator + CONFIG_FILE)),
          StandardCharsets.UTF_8
        )
      )
      config <- IO.fromEither(decode[TransformersConfig](configBytes))
      session <- OnnxSession.load(
        model = new ByteArrayInputStream(modelBytes),
        dic = new ByteArrayInputStream(vocabBytes),
        dim = config.hidden_size
      )
    } yield {
      session
    }

}
