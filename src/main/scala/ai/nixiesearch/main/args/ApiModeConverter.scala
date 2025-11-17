package ai.nixiesearch.main.args

import ai.nixiesearch.main.CliConfig.ApiMode
import ai.nixiesearch.main.CliConfig.ApiMode.{Http, Lambda}

object ApiModeConverter extends ArgConverter[ApiMode] {

  override def convert(value: String): Either[String, ApiMode] = value match {
    case "http"   => Right(Http)
    case "lambda" => Right(Lambda)
    case other    => Left(s"api serving mode ${other} is not supported")
  }
}
