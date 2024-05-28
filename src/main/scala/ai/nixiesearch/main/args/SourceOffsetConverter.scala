package ai.nixiesearch.main.args

import ai.nixiesearch.source.SourceOffset

object SourceOffsetConverter extends ArgConverter[SourceOffset] {
  override def convert(value: String): Either[String, SourceOffset] = SourceOffset.fromString(value) match {
    case Left(ex)     => Left(ex.toString)
    case Right(value) => Right(value)
  }
}
