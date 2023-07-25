package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.LocalModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import cats.effect.IO
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, FileInputStream}

object LocalModelLoader extends Logging {
  implicit val localModelLoader: ModelLoader[LocalModelHandle] = new ModelLoader[LocalModelHandle] {
    override def load(handle: LocalModelHandle, dim: Int, modelFile: String, vocabFile: String): IO[OnnxSession] =
      for {
        _          <- info(s"loading $modelFile from $handle")
        modelBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + modelFile))))
        vocabBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + vocabFile))))
        session <- OnnxSession.load(
          model = new ByteArrayInputStream(modelBytes),
          dic = new ByteArrayInputStream(vocabBytes),
          dim = dim
        )
      } yield {
        session
      }

  }
}
