package ai.nixiesearch.main.subcommands

import ai.nixiesearch.config.Config
import ai.nixiesearch.main.subcommands.StandaloneMode
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StandaloneModeTest extends AnyFlatSpec with Matchers {
  it should "start on dummy mode with no indices" in {
    val conf            = Config()
    val (api, shutdown) = StandaloneMode.api(null, conf).allocated.unsafeRunSync()
    shutdown.unsafeRunSync()
  }
}
