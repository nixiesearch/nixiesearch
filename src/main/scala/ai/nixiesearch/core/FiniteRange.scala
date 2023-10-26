package ai.nixiesearch.core

sealed trait FiniteRange {
  def value: Double
  def name: String
}

object FiniteRange {
  sealed trait Lower extends FiniteRange
  object Lower {
    case class Gt(value: Double) extends Lower {
      val name = "gt"
    }

    case class Gte(value: Double) extends Lower {
      val name = "gte"
    }

    val NEGATIVE_INF = Gte(Int.MinValue.toDouble)
  }

  sealed trait Higher extends FiniteRange

  object Higher {
    case class Lt(value: Double) extends Higher {
      val name = "lt"
    }

    case class Lte(value: Double) extends Higher {
      val name = "lte"
    }
    val POSITIVE_INF = Lte(Int.MaxValue.toFloat)
  }
}
