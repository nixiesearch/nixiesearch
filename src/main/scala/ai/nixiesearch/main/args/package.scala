package ai.nixiesearch.main

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.URL
import ai.nixiesearch.main.CliConfig.{ApiMode, Loglevel}
import ai.nixiesearch.source.SourceOffset
import org.rogach.scallop.ValueConverter

import java.io.File

package object args {
  object implicits {
    given loglevelConverter: ValueConverter[Loglevel]             = LoglevelConverter
    given hostnameConverter: ValueConverter[Hostname]             = HostnameConverter
    given portConverter: ValueConverter[Port]                     = PortConverter
    given urlConverter: ValueConverter[URL]                       = URLConverter
    given fileConverter: ValueConverter[File]                     = FileConverter
    given mapStrStrConverter: ValueConverter[Map[String, String]] = MapStringStringConverter
    given listStrConverter: ValueConverter[List[String]]          = ListStringConverter
    given offsetConverter: ValueConverter[SourceOffset]           = SourceOffsetConverter
    given apiModeConverter: ValueConverter[ApiMode]               = ApiModeConverter
  }
}
