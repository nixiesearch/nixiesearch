package ai.nixiesearch.main.subcommands

import ai.nixiesearch.config.Config
import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.main.CliConfig.CliArgs.SearchArgs
import ai.nixiesearch.util.EnvVars
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Paths

class SearchModeTest extends AnyFlatSpec with Matchers {
  it should "start on dummy mode with no indices" in {
    val conf            = Config()
    val (api, shutdown) = SearchMode
      .api(SearchArgs(config = LocalURL(Paths.get("src/test/resources/config/minimal.yml"))), EnvVars(Map.empty))
      .allocated
      .unsafeRunSync()
    shutdown.unsafeRunSync()
  }
}
