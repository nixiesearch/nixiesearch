package ai.nixiesearch.main.args

import ai.nixiesearch.main.CliConfig.Loglevel
import org.rogach.scallop.{ArgType, ValueConverter}

object LoglevelConverter extends ArgConverter[Loglevel] {
  override def convert(value: String): Either[String, Loglevel] = value.toLowerCase() match {
    case "info"             => Right(Loglevel.INFO)
    case "debug"            => Right(Loglevel.DEBUG)
    case "warn" | "warning" => Right(Loglevel.WARN)
    case "error" | "err"    => Right(Loglevel.ERROR)
    case other              => Left("Loglevel '$other' is not supported, use debug/info/warn/error.")
  }
}
