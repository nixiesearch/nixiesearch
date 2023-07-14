package ai.nixiesearch.config

import ai.nixiesearch.config.StoreConfig.StoreUrl
import ai.nixiesearch.config.StoreConfig.StoreUrl.{LocalStoreUrl, S3StoreUrl}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser._

class StoreConfigTest extends AnyFlatSpec with Matchers {
  it should "decode s3 with prefix" in {
    val result = parse("s3://bucket/prefix").flatMap(_.as[StoreUrl])
    result shouldBe Right(S3StoreUrl("bucket", "prefix"))
  }

  it should "decode s3 without prefix" in {
    val result = parse("s3://bucket").flatMap(_.as[StoreUrl])
    result shouldBe Right(S3StoreUrl("bucket", "nixiesearch"))
  }

  it should "decode file with file:///" in {
    val result = parse("file:///foo/bar").flatMap(_.as[StoreUrl])
    result shouldBe Right(LocalStoreUrl("/foo/bar"))
  }

  it should "decode file with /" in {
    val result = parse("/foo/bar").flatMap(_.as[StoreUrl])
    result shouldBe Right(LocalStoreUrl("/foo/bar"))
  }
}
