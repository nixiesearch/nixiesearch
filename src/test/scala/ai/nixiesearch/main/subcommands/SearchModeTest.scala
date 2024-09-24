package ai.nixiesearch.main.subcommands

import ai.nixiesearch.config.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SearchModeTest extends AnyFlatSpec with Matchers {
  it should "start on dummy mode with no indices" in {
    val conf            = Config()
    val (api, shutdown) = SearchMode.api(null, conf).allocated.unsafeRunSync()
    shutdown.unsafeRunSync()
  }
}
