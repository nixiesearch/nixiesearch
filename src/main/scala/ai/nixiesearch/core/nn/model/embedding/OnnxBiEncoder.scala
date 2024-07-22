package ai.nixiesearch.core.nn.model.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine

import java.nio.LongBuffer
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

case class OnnxBiEncoder(
    env: OrtEnvironment,
    session: OrtSession,
    tokenizer: HuggingFaceTokenizer,
    dim: Int,
    cacheConfig: EmbeddingCacheConfig
) extends Logging {

  val cache = Scaffeine().maximumSize(cacheConfig.maxSize).build[String, Array[Float]]()

  def embed(doc: String): IO[Array[Float]] = embed(Array(doc)).flatMap {
    case x if x.length > 0 => IO.pure(x(0))
    case empty             => IO.raiseError(BackendError(s"got empty embedding for text $doc"))
  }

  def embed(batch: Array[String]): IO[Array[Array[Float]]] = IO {
    val cached         = ArrayBuffer[(String, Array[Float])]()
    val nonCachedBatch = ArrayBuffer[String]()
    var i              = 0
    while (i < batch.length) {
      cache.getIfPresent(batch(i)) match {
        case Some(value) => cached.addOne(batch(i) -> value)
        case None        => nonCachedBatch.addOne(batch(i))
      }
      i += 1
    }
    val nonCachedMap = if (nonCachedBatch.nonEmpty) {
      val encoded = tokenizer.batchEncode(nonCachedBatch.toArray)

      val tokens       = encoded.flatMap(e => e.getIds)
      val tokenLengths = encoded.map(e => e.getAttentionMask.sum.toInt)
      val tokenTypes   = encoded.flatMap(e => e.getTypeIds)
      val attMask      = encoded.flatMap(e => e.getAttentionMask)

      val tensorDim = Array(nonCachedBatch.length.toLong, encoded(0).getIds.length)
      val args = session.getInputNames.asScala.map {
        case n @ "input_ids"      => n -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim)
        case n @ "token_type_ids" => n -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim)
        case n @ "attention_mask" => n -> OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
      }.toMap
      val result     = session.run(args.asJava)
      val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
      val normalized = avgpool(tensor, tokenLengths, dim)
      result.close()
      args.values.foreach(_.close())
      nonCachedBatch.zip(normalized).toMap
    } else {
      Map.empty
    }
    val cachedMap = cached.toMap
    val out       = new Array[Array[Float]](batch.length)
    var j         = 0
    Try {
      while (j < batch.length) {
        cachedMap.get(batch(j)) match {
          case Some(value) => out(j) = value
          case None =>
            nonCachedMap.get(batch(j)) match {
              case Some(value) =>
                out(j) = value
                cache.put(batch(j), value)
              case None =>
                throw BackendError("cache inconsistency detected")
            }
        }
        j += 1
      }
    } match {
      case Failure(exception) =>
        val b = exception
      case Success(value) =>
        val b = 1
    }
    out
  }

  def avgpool(tensor: Array[Array[Array[Float]]], tokenLengths: Array[Int], dim: Int): Array[Array[Float]] = {
    val result = new Array[Array[Float]](tokenLengths.length)
    var s      = 0
    while (s < tensor.length) {
      val embed = new Array[Float](dim)
      var i     = 0
      while (i < dim) {
        var sum = 0.0
        var cnt = 0
        var j   = 0
        while (j < tensor(s).length) {
          if (j < tokenLengths(s)) {
            sum += tensor(s)(j)(i)
            cnt += 1
          }
          j += 1
        }
        embed(i) = (sum / cnt).toFloat
        i += 1
      }
      result(s) = embed
      s += 1
    }
    result
  }

  def close() = {
    logger.debug(s"closing ONNX session $session")
    session.close()
  }

}

object OnnxBiEncoder {
  def apply(session: OnnxSession, cacheConfig: EmbeddingCacheConfig): OnnxBiEncoder =
    OnnxBiEncoder(session.env, session.session, session.tokenizer, session.dim, cacheConfig)
}
