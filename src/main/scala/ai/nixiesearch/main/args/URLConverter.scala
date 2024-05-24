package ai.nixiesearch.main.args

import ai.nixiesearch.config.URL

object URLConverter extends ArgConverter[URL] {
  override def convert(value: String): Either[String, URL] = URL.fromString(value) match {
    case Left(err)    => Left(err.toString)
    case Right(value) => Right(value)
  }
}
