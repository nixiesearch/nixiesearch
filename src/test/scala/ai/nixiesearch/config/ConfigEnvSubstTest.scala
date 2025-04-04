package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.util.EnvVars
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse
import cats.effect.unsafe.implicits.global

import java.nio.charset.StandardCharsets
import scala.util.Try

class ConfigEnvSubstTest extends AnyFlatSpec with Matchers {
  it should "pass host" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val config = Config.load(yaml, EnvVars(Map.empty)).unsafeRunSync()
    config.core.host shouldBe Hostname("0.0.0.0")
  }
  it should "substitute host" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val config = Config.load(yaml, EnvVars(Map("NIXIESEARCH_CORE_HOST" -> "127.0.0.1"))).unsafeRunSync()
    config.core.host shouldBe Hostname("127.0.0.1")
  }
  it should "fail on wrong log level format" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val config = Try(Config.load(yaml, EnvVars(Map("NIXIESEARCH_CORE_LOGLEVEL" -> "nope"))).unsafeRunSync())
    config.isFailure shouldBe true
  }
}
