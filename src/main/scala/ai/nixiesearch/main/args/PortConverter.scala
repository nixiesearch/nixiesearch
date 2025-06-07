package ai.nixiesearch.main.args

import ai.nixiesearch.config.ApiConfig.Port

object PortConverter extends ArgConverter[Port] {
  val MAX_PORT                                              = 1024 * 64
  override def convert(value: String): Either[String, Port] = value.toIntOption match {
    case None                         => Left(s"cannot convert non-numeric '$value' to port'")
    case Some(num) if num <= 0        => Left(s"port value should be positive, but '$num' is not.")
    case Some(num) if num >= MAX_PORT => Left(s"port value should be less than $MAX_PORT, but '$value' is not.")
    case Some(num)                    => Right(Port(num))
  }
}
