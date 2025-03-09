package ai.nixiesearch.core.nn.model.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.PoolingType
import ai.nixiesearch.config.InferenceConfig.PromptConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.GPUUtils
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.providers.OrtCUDAProviderOptions
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtLoggingLevel, OrtSession}
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.jdk.CollectionConverters.*
import java.io.{InputStream, RandomAccessFile}
import java.nio.LongBuffer
import fs2.{Chunk, Stream}

import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Path

sealed trait EmbedModel extends Logging {
  def prompt: PromptConfig
  def batchSize: Int
  // a helper function to simplify query embedding

  def encode(doc: String): IO[Array[Float]] = encodeBatch(List(doc)).flatMap {
    case x if x.length == 1 => IO(x(0))
    case _                  => IO.raiseError(BackendError(s"got empty embedding for doc '$doc'"))
  }
  def encode(docs: List[String]): IO[Array[Array[Float]]] = Stream
    .emits(docs)
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
      inputTensorNames: List[String],
      prompt: PromptConfig,
      poolingType: PoolingType,
      normalize: Boolean
  ) extends EmbedModel {
    override val batchSize = 16
    override def encodeBatch(batch: List[String]): IO[Array[Array[Float]]] = IO {
      val encoded = tokenizer.batchEncode(batch.toArray)

      val tokens       = encoded.flatMap(e => e.getIds)
      val tokenLengths = encoded.map(e => e.getAttentionMask.sum.toInt)
      val tokenTypes   = encoded.flatMap(e => e.getTypeIds)
      val attMask      = encoded.flatMap(e => e.getAttentionMask)

      val tensorDim = Array(batch.length.toLong, encoded(0).getIds.length)
      val argsList = inputTensorNames.map {
        case "input_ids"      => OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim)
        case "token_type_ids" => OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim)
        case "attention_mask" => OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
        // for jina-embed-v3
        case "task_id" => OnnxTensor.createTensor(env, LongBuffer.wrap(Array(4L)), Array(1L))
        case other     => throw Exception(s"input $other not supported")
      }
      val args   = inputTensorNames.zip(argsList).toMap
      val result = session.run(args.asJava)
      val tensor = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
      val normalized = poolingType match {
        case PoolingType.MeanPooling => EmbedPooling.mean(tensor, tokenLengths, dim, normalize)
        case PoolingType.CLSPooling  => EmbedPooling.cls(tensor, tokenLengths, dim, normalize)
      }

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
        model: Path,
        data: Option[Path],
        dic: Path,
        dim: Int,
        prompt: PromptConfig,
        ttidNeeded: Boolean = true,
        threads: Int = ONNX_THREADS_DEFAULT,
        seqlen: Int = 512,
        pooling: PoolingType,
        normalize: Boolean
    ): Resource[IO, OnnxEmbedModel] = for {
      isGPUBuild <- Resource.eval(IO(GPUUtils.isGPUBuild()))
      _          <- Resource.eval(IO.whenA(isGPUBuild)(info(s"Embedding model scheduled for GPU inference")))
      model <- Resource.make(
        IO(createUnsafe(model, dic, dim, prompt, ttidNeeded, isGPUBuild, threads, seqlen, pooling, normalize))
      )(e => e.close())
    } yield {
      model
    }

    def createUnsafe(
        model: Path,
        dic: Path,
        dim: Int,
        prompt: PromptConfig,
        ttidNeeded: Boolean = true,
        gpu: Boolean = false,
        threads: Int = ONNX_THREADS_DEFAULT,
        seqlen: Int = 512,
        pooling: PoolingType,
        normalize: Boolean
    ) = {
      val tokenizer = HuggingFaceTokenizer.newInstance(
        dic,
        Map("padding" -> "true", "truncation" -> "true", "modelMaxLength" -> seqlen.toString).asJava
      )

      val env  = OrtEnvironment.getEnvironment("sbert")
      val opts = new SessionOptions()
      opts.setIntraOpNumThreads(threads)
      opts.setOptimizationLevel(OptLevel.ALL_OPT)
      if (logger.isDebugEnabled) opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
      if (gpu) opts.addCUDA(0)

      val session = env.createSession(model.toString, opts)
      val inputs  = session.getInputNames.asScala.toList
      val outputs = session.getOutputNames.asScala.toList
      logger.info(s"Loaded ONNX model (inputs=$inputs outputs=$outputs dim=$dim)")
      OnnxEmbedModel(env, session, tokenizer, dim, inputs, prompt, pooling, normalize)
    }
  }

  case class OpenAIEmbedModel(prompt: PromptConfig) extends EmbedModel {
    override def encodeBatch(docs: List[String]): IO[Array[Array[Float]]] = ???
    override def close(): IO[Unit]                                        = ???
    override def batchSize: Int                                           = 32
  }
}
