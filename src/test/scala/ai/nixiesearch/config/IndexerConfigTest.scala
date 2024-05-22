package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.IndexerConfig.IndexerSourceConfig.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.*

import java.nio.file.Paths

class IndexerConfigTest extends AnyFlatSpec with Matchers {
  it should "decode api indexer config" in {
    val yml =
      """
        |api:
        |  host: 1.1.1.1
        |  port: 80""".stripMargin
    val parsed = parse(yml).flatMap(_.as[IndexerConfig])
    parsed shouldBe Right(IndexerConfig(ApiSourceConfig(Hostname("1.1.1.1"), Port(80))))
  }

  it should "decode file indexer config" in {
    val yml =
      """
        |file:
        |  path: file://path/to/file.json
        |  recursive: false""".stripMargin
    val parsed = parse(yml).flatMap(_.as[IndexerConfig])
    parsed shouldBe Right(IndexerConfig(FileSourceConfig(URL.LocalURL(Paths.get("/path/to/file.json")))))
  }
}
