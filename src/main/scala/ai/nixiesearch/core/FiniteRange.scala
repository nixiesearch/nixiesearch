package ai.nixiesearch.core

sealed trait FiniteRange {
  def value: Double
  def name: String
  def inclusive: Boolean
}

object FiniteRange {
  sealed trait Lower extends FiniteRange
  object Lower {
    case class Gt(value: Double) extends Lower {
      val name      = "gt"
      val inclusive = false
    }

    case class Gte(value: Double) extends Lower {
      val name      = "gte"
      val inclusive = true
    }
    
    val NEGATIVE_INF = Gte(Int.MinValue.toDouble)
  }

  sealed trait Higher extends FiniteRange

  object Higher {
    case class Lt(value: Double) extends Higher {
      val name      = "lt"
      val inclusive = false
    }

    case class Lte(value: Double) extends Higher {
      val name      = "lte"
      val inclusive = true
    }

    val POSITIVE_INF = Lte(Int.MaxValue.toFloat)
  }
}
