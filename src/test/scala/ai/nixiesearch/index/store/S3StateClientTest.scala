package ai.nixiesearch.index.store

import ai.nixiesearch.config.StoreConfig.BlockStoreLocation
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import scala.util.Random

class S3StateClientTest extends StateClientSuite[S3StateClient] {

  override def client(): S3StateClient = {
    val conf = BlockStoreLocation.S3Location(
      bucket = "bucket",
      region = Some("us-east-1"),
      prefix = s"test_${Random.nextInt(1000000)}",
      endpoint = Some("http://localhost:4566")
    )
    S3StateClient.create(conf, TestIndexMapping()).unsafeRunSync()
  }
}
