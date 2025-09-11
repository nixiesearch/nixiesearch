package ai.nixiesearch.config.mapping

import ai.nixiesearch.util.Size
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import org.apache.lucene.index.{
  LogByteSizeMergePolicy,
  LogDocMergePolicy,
  MergePolicy,
  NoMergePolicy,
  TieredMergePolicy
}

sealed trait MergePolicyConfig {
  def toLuceneMergePolicy(): MergePolicy
}

object MergePolicyConfig {
  case class NoMergePolicyConfig() extends MergePolicyConfig {
    override def toLuceneMergePolicy(): MergePolicy = NoMergePolicy.INSTANCE
  }

  given noMergePolicyDecoder: Decoder[NoMergePolicyConfig] = Decoder.const(NoMergePolicyConfig())

  given noMergePolicyEncoder: Encoder[NoMergePolicyConfig] = deriveEncoder[NoMergePolicyConfig]

  case class LogByteSizeMergePolicyConfig(
      max_merge_size: Size = Size.mb(LogByteSizeMergePolicy.DEFAULT_MAX_MERGE_MB),
      min_merge_size: Size = Size.mb(LogByteSizeMergePolicy.DEFAULT_MIN_MERGE_MB),
      min_merge_size_for_forced_merge: Size = Size.mb(LogByteSizeMergePolicy.DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE)
  ) extends MergePolicyConfig {
    override def toLuceneMergePolicy(): MergePolicy = {
      val policy = new LogByteSizeMergePolicy()
      policy.setMaxMergeMB(max_merge_size.mb.toDouble)
      policy.setMinMergeMB(min_merge_size.mb.toDouble)
      policy.setMaxMergeMBForForcedMerge(min_merge_size_for_forced_merge.mb.toDouble)
      policy
    }
  }

  given logByteSizeMergePolicyDecoder: Decoder[LogByteSizeMergePolicyConfig] = Decoder.instance(c =>
    for {
      max_merge_size                  <- c.downField("max_merge_size").as[Option[Size]]
      min_merge_size                  <- c.downField("min_merge_size").as[Option[Size]]
      min_merge_size_for_forced_merge <- c.downField("min_merge_size_for_forced_merge").as[Option[Size]]
    } yield {
      val default = LogByteSizeMergePolicyConfig()
      LogByteSizeMergePolicyConfig(
        max_merge_size = max_merge_size.getOrElse(default.max_merge_size),
        min_merge_size = min_merge_size.getOrElse(default.min_merge_size),
        min_merge_size_for_forced_merge =
          min_merge_size_for_forced_merge.getOrElse(default.min_merge_size_for_forced_merge)
      )
    }
  )

  given logByteSizeMergePolicyEncoder: Encoder[LogByteSizeMergePolicyConfig] =
    deriveEncoder[LogByteSizeMergePolicyConfig]

  case class LogDocMergePolicyConfig(
      min_merge_docs: Int = LogDocMergePolicy.DEFAULT_MIN_MERGE_DOCS,
      max_merge_docs: Int = Int.MaxValue
  ) extends MergePolicyConfig {
    override def toLuceneMergePolicy(): MergePolicy = {
      val policy = new LogDocMergePolicy()
      policy.setMinMergeDocs(min_merge_docs)
      policy.setMaxMergeDocs(max_merge_docs)
      policy
    }
  }

  given logDocMergePolicyDecoder: Decoder[LogDocMergePolicyConfig] = Decoder.instance(c =>
    for {
      min_merge_docs <- c.downField("min_merge_docs").as[Option[Int]]
      max_merge_docs <- c.downField("max_merge_docs").as[Option[Int]]
    } yield {
      val default = LogDocMergePolicyConfig()
      LogDocMergePolicyConfig(
        min_merge_docs = min_merge_docs.getOrElse(default.min_merge_docs),
        max_merge_docs = max_merge_docs.getOrElse(default.max_merge_docs)
      )
    }
  )

  given logDocMergePolicyEncoder: Encoder[LogDocMergePolicyConfig] = deriveEncoder[LogDocMergePolicyConfig]

  case class TieredMergePolicyConfig(
      segments_per_tier: Int = 10,
      max_merge_at_once: Int = 10,
      max_merged_segment_size: Size = Size.mb(5.0 * 1024),
      floor_segment_size: Size = Size.mb(16.0),
      target_search_concurrency: Int = 1
  ) extends MergePolicyConfig {
    override def toLuceneMergePolicy(): MergePolicy = {
      val policy = new TieredMergePolicy()
      policy.setSegmentsPerTier(segments_per_tier)
      policy.setMaxMergeAtOnce(max_merge_at_once)
      policy.setMaxMergedSegmentMB(max_merged_segment_size.mb.toDouble)
      policy.setFloorSegmentMB(floor_segment_size.mb.toDouble)
      policy.setTargetSearchConcurrency(target_search_concurrency)
      policy
    }
  }

  given tieredMergePolicyDecoder: Decoder[TieredMergePolicyConfig] = Decoder.instance(c =>
    for {
      segments_per_tier         <- c.downField("segments_per_tier").as[Option[Int]]
      max_merge_at_once         <- c.downField("max_merge_at_once").as[Option[Int]]
      max_merged_segment_size   <- c.downField("max_merged_segment_size").as[Option[Size]]
      floor_segment_size        <- c.downField("floor_segment_size").as[Option[Size]]
      target_search_concurrency <- c.downField("target_search_concurrency").as[Option[Int]]
    } yield {
      val default = TieredMergePolicyConfig()
      TieredMergePolicyConfig(
        segments_per_tier = segments_per_tier.getOrElse(default.segments_per_tier),
        max_merge_at_once = max_merge_at_once.getOrElse(default.max_merge_at_once),
        max_merged_segment_size = max_merged_segment_size.getOrElse(default.max_merged_segment_size),
        floor_segment_size = floor_segment_size.getOrElse(default.floor_segment_size),
        target_search_concurrency = target_search_concurrency.getOrElse(default.target_search_concurrency)
      )
    }
  )

  given tieredMergePolicyEncoder: Encoder[TieredMergePolicyConfig] = deriveEncoder[TieredMergePolicyConfig]

  given mergePolicyConfigEncoder: Encoder[MergePolicyConfig] = Encoder.instance {
    case p: NoMergePolicyConfig          => Json.fromString("none")
    case p: LogByteSizeMergePolicyConfig => Json.obj("byte_size" -> logByteSizeMergePolicyEncoder(p))
    case p: LogDocMergePolicyConfig      => Json.obj("doc_count" -> logDocMergePolicyEncoder(p))
    case p: TieredMergePolicyConfig      => Json.obj("tiered" -> tieredMergePolicyEncoder(p))
  }

  given mergePolicyConfigDecoder: Decoder[MergePolicyConfig] = Decoder.instance(c => {
    c.value.as[String] match {
      case Right("none")      => Right(NoMergePolicyConfig())
      case Right("tiered")    => Right(TieredMergePolicyConfig())
      case Right("doc_count") => Right(LogDocMergePolicyConfig())
      case Right("byte_size") => Right(LogByteSizeMergePolicyConfig())
      case Right(other)       => Left(DecodingFailure(s"'$other' is not a supported merge policy", c.history))
      case Left(_)            =>
        c.value.asObject match {
          case Some(obj) =>
            obj.toList match {
              case Nil =>
                Left(
                  DecodingFailure(
                    s"merge policy should be either false or json-object, but got ${c.value}",
                    c.history
                  )
                )
              case ("none", json) :: Nil      => Right(NoMergePolicyConfig())
              case ("byte_size", json) :: Nil => json.as[LogByteSizeMergePolicyConfig]
              case ("doc_count", json) :: Nil => json.as[LogDocMergePolicyConfig]
              case ("tiered", json) :: Nil    => json.as[TieredMergePolicyConfig]
              case (other, _) :: Nil => Left(DecodingFailure(s"merge policy '$other' is not supported", c.history))
              case _ :: tail         =>
                Left(
                  DecodingFailure(
                    s"merge policy json object can only have single key, but got ${obj.keys.toList}",
                    c.history
                  )
                )
            }
          case None =>
            Left(
              DecodingFailure(s"merge policy should be either false or json-object, but got ${c.value}", c.history)
            )
        }
    }
  })
}
