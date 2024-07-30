package ai.nixiesearch.config

import ai.nixiesearch.config.CacheConfigTest.ConfigWrapper
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*
import io.circe.generic.semiauto.*

class CacheConfigTest extends AnyFlatSpec with Matchers {
  it should "decode explicit" in {
    val yaml   = "cache:\n  dir: /cache"
    val parsed = parse(yaml).flatMap(_.as[ConfigWrapper])
    parsed shouldBe Right(ConfigWrapper(CacheConfig("/cache")))
  }
}

object CacheConfigTest {
  case class ConfigWrapper(cache: CacheConfig)
  given wrapperDecoder: Decoder[ConfigWrapper] = deriveDecoder
}
