package ai.nixiesearch.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import io.circe.yaml.parser.{parse => parseYaml}
import io.circe.syntax.given
import io.circe.parser.{decode => decodeJson}

import java.nio.file.Paths

class StoreConfigTest extends AnyFlatSpec with Matchers {
  "yaml decoder" should "decode local disk config" in {
    import StoreConfig.yaml.given
    val yaml =
      """
        |local:
        |  disk:
        |    path: /foo/bar""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[StoreConfig])
    decoded shouldBe Right(StoreConfig.LocalStoreConfig(local = DiskLocation(Paths.get("/foo/bar"))))
  }

  it should "decode local mem config" in {
    import StoreConfig.yaml.given
    val yaml =
      """
        |local:
        |  memory:""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[StoreConfig])
    decoded shouldBe Right(StoreConfig.LocalStoreConfig(local = MemoryLocation()))
  }

  it should "decode distributed config with defaults" in {
    import StoreConfig.yaml.given
    val yaml =
      """
        |distributed:
        |  remote:
        |    s3:
        |      bucket: foo
        |      prefix: bar""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[StoreConfig])
    decoded shouldBe Right(StoreConfig.DistributedStoreConfig(remote = S3Location("foo", "bar")))
  }

  it should "decode full config" in {
    import StoreConfig.yaml.given
    val yaml = """
                 |distributed:
                 |  searcher:
                 |    memory:
                 |  indexer:
                 |    memory:
                 |  remote:
                 |    s3:
                 |      bucket: foo
                 |      prefix: bar""".stripMargin
    val decoded = parseYaml(yaml).flatMap(_.as[StoreConfig])
    decoded shouldBe Right(
      StoreConfig.DistributedStoreConfig(
        remote = S3Location("foo", "bar"),
        searcher = MemoryLocation(),
        indexer = MemoryLocation()
      )
    )
  }

  "json codec" should "roundtrip local config" in {
    import StoreConfig.json.given
    val conf: StoreConfig = StoreConfig.LocalStoreConfig(local = DiskLocation(Paths.get("/foo/bar")))
    val json              = conf.asJson.noSpaces
    val decoded           = decodeJson[StoreConfig](json)
    decoded shouldBe Right(conf)
  }

  it should "roundtrip distributed config" in {
    import StoreConfig.json.given
    val conf: StoreConfig = StoreConfig.DistributedStoreConfig(
      remote = S3Location("foo", "bar"),
      searcher = MemoryLocation(),
      indexer = MemoryLocation()
    )
    val json    = conf.asJson.noSpaces
    val decoded = decodeJson[StoreConfig](json)
    decoded shouldBe Right(conf)
  }
}
