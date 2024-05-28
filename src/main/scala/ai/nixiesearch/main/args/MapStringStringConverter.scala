package ai.nixiesearch.main.args

object MapStringStringConverter extends ArgConverter[Map[String, String]] {
  override def convert(value: String): Either[String, Map[String, String]] = {
    val tuples = value.split(',')
    tuples.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      case (Left(err), _)   => Left(err)
      case (Right(map), "") => Right(map)
      case (Right(map), next) =>
        next.split('=').toList match {
          case Nil                  => Right(map)
          case "" :: _ :: Nil       => Left(s"key name cannot be empty: '$next'")
          case _ :: "" :: Nil       => Left(s"value cannot be empty: '$next'")
          case left :: right :: Nil => Right(map + (left -> right))
          case _                    => Left(s"wrong number of arguments: $next")
        }
    }
  }
}
