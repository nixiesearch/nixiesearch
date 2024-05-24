package ai.nixiesearch.main.args

import ai.nixiesearch.config.ApiConfig.Hostname

object HostnameConverter extends ArgConverter[Hostname] {
  override def convert(value: String): Either[String, Hostname] = value match {
    case ""    => Left("hostname cannot be empty")
    case other => Right(Hostname(value))
  }
}
