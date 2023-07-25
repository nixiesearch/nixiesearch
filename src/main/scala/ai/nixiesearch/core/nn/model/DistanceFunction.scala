package ai.nixiesearch.core.nn.model

import org.apache.lucene.util.VectorUtil

sealed trait DistanceFunction {
  def dist(query: Array[Float], item: Array[Float]): Float
}

object DistanceFunction {
  case object CosineDistance extends DistanceFunction {
    override def dist(query: Array[Float], item: Array[Float]): Float = {
      VectorUtil.cosine(query, item)
    }
  }

  case object DotDistance extends DistanceFunction {
    override def dist(query: Array[Float], item: Array[Float]): Float =
      VectorUtil.dotProduct(query, item)
  }
}
