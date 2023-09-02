package ai.nixiesearch.main

import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Files

class CliConfigTest extends AnyFlatSpec with Matchers {
  it should "parse standalone args" in {
    val temp   = Files.createTempFile("config", ".yml")
    val result = CliConfig.load(List("standalone", "--config", temp.toString)).unsafeRunSync()
    result shouldBe StandaloneArgs(Some(temp.toFile))
  }
}
