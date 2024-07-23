package ai.nixiesearch.core.nn.model.embedding

object EmbedPooling {
  def mean(tensor: Array[Array[Array[Float]]], tokenLengths: Array[Int], dim: Int): Array[Array[Float]] = {
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

  def cls(tensor: Array[Array[Array[Float]]], tokenLengths: Array[Int], dimensions: Int): Array[Array[Float]] = {
    val result     = new Array[Array[Float]](tokenLengths.length)
    var batchIndex = 0
    while (batchIndex < tensor.length) {
      val embed    = new Array[Float](dimensions)
      var dimIndex = 0
      while (dimIndex < dimensions) {
        embed(dimIndex) = tensor(batchIndex)(0)(dimIndex)
        dimIndex += 1
      }
      result(batchIndex) = embed
      batchIndex += 1
    }
    result
  }
}
