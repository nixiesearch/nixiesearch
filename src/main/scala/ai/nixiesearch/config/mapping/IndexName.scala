package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder, KeyDecoder}

import scala.util.{Failure, Success}

case class IndexName(value: String) {}

object IndexName {
  val indexNameRegex = "^([a-zA-Z]+[a-zA-Z0-9_]*)$".r

  def of(name: String): Either[UserError, IndexName] = name match {
    case indexNameRegex(name) => Right(new IndexName(name))
    case _ =>
      Left(UserError(s"index name should start with [a-zA-Z] and only contain [a-zA-Z0-9_]* characters"))
  }
  given indexNameEncoder: Encoder[IndexName] = Encoder.encodeString.contramap(in => in.value)
  given indexNameDecoder: Decoder[IndexName] = Decoder.decodeString.emapTry(str => IndexName.of(str).toTry)
  given indexNameKeyDecoder: KeyDecoder[IndexName] = new KeyDecoder[IndexName] {
    override def apply(key: String): Option[IndexName] = IndexName.of(key).toOption
  }

}
