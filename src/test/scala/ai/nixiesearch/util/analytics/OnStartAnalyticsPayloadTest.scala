package ai.nixiesearch.util.analytics

import ai.nixiesearch.config.Config
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import cats.effect.unsafe.implicits.global
import java.nio.charset.StandardCharsets

class OnStartAnalyticsPayloadTest extends AnyFlatSpec with Matchers {
  it should "generate payload" in {
    val yaml   = IOUtils.resourceToString("/config/config.yml", StandardCharsets.UTF_8)
    val parsed = parse(yaml).flatMap(_.as[Config]).toOption.get

    val result = OnStartAnalyticsPayload.create(parsed, "standalone").unsafeRunSync()
    result.system.os shouldBe System.getProperty("os.name")
  }
}
