package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

case class IndexName(value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case IndexName(name) => name == value
    case s: String       => s == value
    case _               => false
  }
}

object IndexName {
  val indexNameRegex = "^([a-zA-Z]+[a-zA-Z0-9_]*)$".r

  def unsafe(name: String) = new IndexName(name)
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
  given indexNameKeyEncoder: KeyEncoder[IndexName] = new KeyEncoder[IndexName] {
    override def apply(key: IndexName): String = key.value
  }

  // given eqNameString: CanEqual[IndexName, String]  = CanEqual.derived
  // given eqStringName: CanEqual[String, IndexName]  = CanEqual.derived
}
