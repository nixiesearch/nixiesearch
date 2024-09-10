package ai.nixiesearch.core.nn.model.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.config.InferenceConfig.PromptConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.jdk.CollectionConverters.*
import java.io.InputStream
import java.nio.LongBuffer
import fs2.{Chunk, Stream}

sealed trait EmbedModel extends Logging {
  def prompt: PromptConfig
  def batchSize: Int
  // a helper function to simplify query embedding
  def encodeQuery(queries: List[String]): IO[Array[Array[Float]]] = encodeBatch(
    queries.map(query => prompt.query + query)
  )
  def encodeQuery(query: String): IO[Array[Float]] = encodeBatch(List(prompt.query + query)).flatMap {
    case x if x.length == 1 => IO(x(0))
    case _                  => IO.raiseError(BackendError(s"got empty embedding for doc '$query'"))
  }

  def encodeDocuments(docs: List[String]): IO[Array[Array[Float]]] =
    Stream
      .emits(docs)
      .map(doc => prompt.doc + doc)
      .chunkN(batchSize)
      .evalMap(batch => encodeBatch(batch.toList).map(batch => Chunk.array(batch)))
      .unchunks
      .compile
      .toList
      .map(_.toArray)

  protected def encodeBatch(batch: List[String]): IO[Array[Array[Float]]]
  def close(): IO[Unit]
}

object EmbedModel {
  case class OnnxEmbedModel(
      env: OrtEnvironment,
      session: OrtSession,
      tokenizer: HuggingFaceTokenizer,
      dim: Int,
      ttidNeeded: Boolean,
      prompt: PromptConfig
  ) extends EmbedModel {
    override val batchSize = 16
    override def encodeBatch(batch: List[String]): IO[Array[Array[Float]]] = IO {
      val encoded = tokenizer.batchEncode(batch.toArray)

      val tokens       = encoded.flatMap(e => e.getIds)
      val tokenLengths = encoded.map(e => e.getAttentionMask.sum.toInt)
      val tokenTypes   = encoded.flatMap(e => e.getTypeIds)
      val attMask      = encoded.flatMap(e => e.getAttentionMask)

      val tensorDim = Array(batch.length.toLong, encoded(0).getIds.length)
      val args =
        if (ttidNeeded)
          Map(
            "input_ids"      -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim),
            "token_type_ids" -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim),
            "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
          )
        else {
          Map(
            "input_ids"      -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim),
            "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
          )
        }
      val result     = session.run(args.asJava)
      val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
      val normalized = EmbedPooling.mean(tensor, tokenLengths, dim)
      result.close()
      args.values.foreach(_.close())
      normalized
    }

    override def close(): IO[Unit] = IO {
      logger.debug(s"closing ONNX session $session")
      session.close()
    }
  }

  object OnnxEmbedModel extends Logging {
    val ONNX_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()
    def create(
        model: InputStream,
        dic: InputStream,
        dim: Int,
        prompt: PromptConfig,
        ttidNeeded: Boolean = true,
        gpu: Boolean = false,
        threads: Int = ONNX_THREADS_DEFAULT,
        seqlen: Int = 512
    ): Resource[IO, OnnxEmbedModel] =
      Resource.make(IO(createUnsafe(model, dic, dim, prompt, ttidNeeded, gpu, threads, seqlen)))(e => e.close())

    def createUnsafe(
        model: InputStream,
        dic: InputStream,
        dim: Int,
        prompt: PromptConfig,
        ttidNeeded: Boolean = true,
        gpu: Boolean = false,
        threads: Int = ONNX_THREADS_DEFAULT,
        seqlen: Int = 512
    ) = {
      val tokenizer = HuggingFaceTokenizer.newInstance(
        dic,
        Map("padding" -> "true", "truncation" -> "true", "modelMaxLength" -> seqlen.toString).asJava
      )

      val env  = OrtEnvironment.getEnvironment("sbert")
      val opts = new SessionOptions()
      opts.setIntraOpNumThreads(threads)
      opts.setOptimizationLevel(OptLevel.ALL_OPT)
      if (gpu) opts.addCUDA(0)
      val modelBytes = IOUtils.toByteArray(model)
      val session    = env.createSession(modelBytes, opts)
      val size       = FileUtils.byteCountToDisplaySize(modelBytes.length)
      val inputs     = session.getInputNames.asScala.toList
      val outputs    = session.getOutputNames.asScala.toList
      logger.info(s"Loaded ONNX model (size=$size inputs=$inputs outputs=$outputs dim=$dim)")
      model.close()
      dic.close()
      OnnxEmbedModel(env, session, tokenizer, dim, ttidNeeded, prompt)
    }
  }

  case class OpenAIEmbedModel(prompt: PromptConfig) extends EmbedModel {
    override def encodeBatch(docs: List[String]): IO[Array[Array[Float]]] = ???
    override def close(): IO[Unit]                                        = ???
    override def batchSize: Int                                           = 32
  }
}
