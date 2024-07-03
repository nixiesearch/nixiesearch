package ai.nixiesearch.main

import ai.nixiesearch.config.URL.{LocalURL, S3URL}
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSourceArgs.FileIndexSourceArgs
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, StandaloneArgs}
import ai.nixiesearch.main.CliConfig.Loglevel.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Files

class CliConfigTest extends AnyFlatSpec with Matchers {
  it should "parse standalone args" in {
    val temp   = Files.createTempFile("config", ".yml")
    val result = CliConfig.load(List("standalone", "--config", temp.toString)).unsafeRunSync()
    result shouldBe StandaloneArgs(LocalURL(temp), INFO)
  }

  it should "parse standalone args with s3 urls" in {
    val result = CliConfig.load(List("standalone", "--config", "s3://bucket/prefix/config.yml")).unsafeRunSync()
    result shouldBe StandaloneArgs(S3URL("bucket", "prefix/config.yml"), INFO)
  }

  it should "parse loglevel for standalone" in {
    val temp   = Files.createTempFile("config", ".yml")
    val result = CliConfig.load(List("standalone", "--config", temp.toString, "--loglevel", "debug")).unsafeRunSync()
    result shouldBe StandaloneArgs(LocalURL(temp), DEBUG)
  }

  it should "parse index file args" in {
    val temp = Files.createTempFile("config", ".yml")
    val result = CliConfig
      .load(List("index", "file", "--config", temp.toString, "--url", "s3://bucket/prefix.json", "--index", "movies"))
      .unsafeRunSync()
    result shouldBe IndexArgs(LocalURL(temp), FileIndexSourceArgs(S3URL("bucket", "prefix.json"), "movies"))
  }
}
