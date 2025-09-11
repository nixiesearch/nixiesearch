package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.IndexerConfig
import ai.nixiesearch.config.mapping.MergePolicyConfig.{NoMergePolicyConfig, TieredMergePolicyConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse
import io.circe.syntax.*

class MergePolicyConfigTest extends AnyFlatSpec with Matchers {
  it should "decode none as string" in {
    val yaml =
      """
        |indexer:
        |  merge_policy: none""".stripMargin
    parse(yaml).flatMap(_.as[IndexConfig]) shouldBe Right(
      IndexConfig(IndexerConfig(merge_policy = Some(NoMergePolicyConfig())))
    )
  }

  it should "decode tiered as string" in {
    val yaml =
      """
        |indexer:
        |  merge_policy: tiered""".stripMargin
    parse(yaml).flatMap(_.as[IndexConfig]) shouldBe Right(
      IndexConfig(IndexerConfig(merge_policy = Some(TieredMergePolicyConfig())))
    )
  }

  it should "decode object" in {
    val yaml =
      """
        |indexer:
        |  merge_policy:
        |    tiered:
        |      segments_per_tier: 100
        |      """.stripMargin
    parse(yaml).flatMap(_.as[IndexConfig]) shouldBe Right(
      IndexConfig(IndexerConfig(merge_policy = Some(TieredMergePolicyConfig(segments_per_tier = 100))))
    )
  }

}
