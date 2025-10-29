package ai.nixiesearch.core.nn.model.embedding

object EmbedPooling {
  def mean(
      tensor: Array[Array[Array[Float]]],
      tokenLengths: Array[Int],
      dimensions: Int,
      normalize: Boolean
  ): Array[Array[Float]] = {
    val result     = new Array[Array[Float]](tokenLengths.length)
    var batchIndex = 0
    while (batchIndex < tensor.length) {
      val embed    = new Array[Float](dimensions)
      var dimIndex = 0
      while (dimIndex < dimensions) {
        var sum        = 0.0
        var cnt        = 0
        var tokenIndex = 0
        while (tokenIndex < tensor(batchIndex).length) {
          if (tokenIndex < tokenLengths(batchIndex)) {
            sum += tensor(batchIndex)(tokenIndex)(dimIndex)
            cnt += 1
          }
          tokenIndex += 1
        }
        embed(dimIndex) = (sum / cnt).toFloat
        dimIndex += 1
      }
      result(batchIndex) = if (normalize) norm(embed) else embed
      batchIndex += 1
    }
    result
  }

  def cls(
      tensor: Array[Array[Array[Float]]],
      tokenLengths: Array[Int],
      dimensions: Int,
      normalize: Boolean
  ): Array[Array[Float]] = {
    val result     = new Array[Array[Float]](tokenLengths.length)
    var batchIndex = 0
    while (batchIndex < tensor.length) {
      val embed    = new Array[Float](dimensions)
      var dimIndex = 0
      while (dimIndex < dimensions) {
        val item = tensor(batchIndex)(0)(dimIndex)
        embed(dimIndex) = item
        dimIndex += 1
      }
      result(batchIndex) = if (normalize) norm(embed) else embed
      batchIndex += 1
    }
    result
  }

  def lastToken(
      tensor: Array[Array[Array[Float]]],
      tokenLengths: Array[Int],
      dimensions: Int,
      normalize: Boolean
  ): Array[Array[Float]] = {
    val result     = new Array[Array[Float]](tokenLengths.length)
    var batchIndex = 0
    while (batchIndex < tensor.length) {
      val embed        = new Array[Float](dimensions)
      val lastTokenIdx = tokenLengths(batchIndex) - 1
      var dimIndex     = 0
      while (dimIndex < dimensions) {
        val item = tensor(batchIndex)(lastTokenIdx)(dimIndex)
        embed(dimIndex) = item
        dimIndex += 1
      }
      result(batchIndex) = if (normalize) norm(embed) else embed
      batchIndex += 1
    }
    result
  }

  // F.normalize like SBERT does
  def norm(embed: Array[Float], eps: Float = 1e-12): Array[Float] = {
    var i         = 0
    var squareSum = 0.0f
    val result    = new Array[Float](embed.length)
    while (i < embed.length) {
      squareSum += embed(i) * embed(i)
      i += 1
    }
    val l2norm = math.max(math.sqrt(squareSum), eps).toFloat
    var j      = 0
    while (j < embed.length) {
      result(j) = embed(j) / l2norm
      j += 1
    }
    result
  }

}
