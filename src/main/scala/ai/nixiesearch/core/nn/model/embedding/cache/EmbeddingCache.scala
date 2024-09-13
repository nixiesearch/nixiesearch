package ai.nixiesearch.core.nn.model.embedding.cache

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.cache.EmbeddingCache.CacheKey
import cats.effect.IO

import scala.collection.mutable.ArrayBuffer

trait EmbeddingCache {
  def put(keys: List[CacheKey], values: Array[Array[Float]]): IO[Unit]

  def get(key: CacheKey): IO[Option[Array[Float]]] = get(List(key)).flatMap {
    case x if x.length == 1 => IO.pure(x(0))
    case other => IO.raiseError(BackendError(s"cache impl expected to return single element, but got $other"))
  }
  def get(keys: List[CacheKey]): IO[Array[Option[Array[Float]]]]

  def getOrEmbedAndCache(
      handle: ModelRef,
      docs: List[String],
      embed: List[String] => IO[Array[Array[Float]]]
  ): IO[Array[Array[Float]]] =
    for {
      keys                              <- IO(docs.map(doc => CacheKey(handle = handle, string = doc)))
      cached                            <- get(keys)
      (nonCachedIndices, nonCachedDocs) <- selectUncached(cached, keys.toArray)
      nonCachedEmbeddings               <- embed(nonCachedDocs.toList.map(_.string))
      _                                 <- put(nonCachedDocs.toList, nonCachedEmbeddings)
      merged                            <- mergeCachedUncached(cached, nonCachedIndices, nonCachedEmbeddings)
    } yield {
      merged
    }

  private def selectUncached(
      cached: Array[Option[Array[Float]]],
      docs: Array[CacheKey]
  ): IO[(Array[Int], Array[CacheKey])] = IO {
    val indices = ArrayBuffer[Int]()
    val ds      = ArrayBuffer[CacheKey]()
    var i       = 0
    while (i < cached.length) {
      if (cached(i).isEmpty) {
        ds.append(docs(i))
        indices.append(i)
      }
      i += 1
    }
    (indices.toArray, ds.toArray)
  }

  private def mergeCachedUncached(
      cached: Array[Option[Array[Float]]],
      uncachedIndices: Array[Int],
      uncachedEmbeds: Array[Array[Float]]
  ): IO[Array[Array[Float]]] = IO {
    val result = new Array[Array[Float]](cached.length)
    var i      = 0
    while (i < cached.length) {
      cached(i).foreach(emb => result(i) = emb)
      i += 1
    }
    var j = 0
    while (j < uncachedIndices.length) {
      val index = uncachedIndices(j)
      result(index) = uncachedEmbeds(j)
      j += 1
    }
    result
  }

}

object EmbeddingCache {
  case class CacheKey(handle: ModelRef, string: String)
}
