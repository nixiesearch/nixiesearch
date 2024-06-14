package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder, KeyDecoder}

import scala.util.{Failure, Success}

object IndexNames {
  opaque type IndexName = String

  object IndexName {
    def apply(name: String): IndexName = name

    val indexNameRegex = "^([a-zA-Z]+[a-zA-Z0-9_]*)$".r

    given indexNameEncoder: Encoder[IndexName] = Encoder.encodeString
    given indexNameDecoder: Decoder[IndexName] = Decoder.decodeString.emapTry {
      case indexNameRegex(name) => Success(name)
      case _ => Failure(UserError(s"index name should start with [a-zA-Z] and only contain [a-zA-Z0-9_]* characters"))
    }
    given indexNameKeyDecoder: KeyDecoder[IndexName] =
      KeyDecoder.decodeKeyString.apply(s => Some(IndexName(s)))

    given nameStringEq: CanEqual[IndexName, String] = CanEqual.derived
    given stringNameEq: CanEqual[String, IndexName] = CanEqual.derived

  }

  extension (self: IndexName) {
    override def toString(): String = self
    override def equals(rhs: Any): Boolean = rhs match {
      case s: String    => s.equals(rhs)
      case s: IndexName => s.equals(rhs)
      case _            => false
    }
  }

}
