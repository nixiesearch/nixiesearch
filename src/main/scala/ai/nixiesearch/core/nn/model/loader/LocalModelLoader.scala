package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.LocalModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import ai.nixiesearch.core.nn.model.loader.ModelLoader.TransformersConfig
import cats.effect.IO
import org.apache.commons.io.IOUtils
import io.circe.parser.*

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.charset.StandardCharsets

object LocalModelLoader extends Logging with ModelLoader[LocalModelHandle] {
    override def load(handle: LocalModelHandle, modelFile: String = MODEL_FILE): IO[OnnxSession] =
      for {
        _          <- info(s"loading $modelFile from $handle")
        modelBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + modelFile))))
        vocabBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + VOCAB_FILE))))
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

