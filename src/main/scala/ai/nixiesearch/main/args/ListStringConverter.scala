package ai.nixiesearch.main.args

object ListStringConverter extends ArgConverter[List[String]] {
  override def convert(value: String): Either[String, List[String]] = Right(value.split(',').toList)
}
