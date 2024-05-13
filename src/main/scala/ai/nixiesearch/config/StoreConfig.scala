package ai.nixiesearch.config

import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import ai.nixiesearch.util.PathJson.given
import io.circe.derivation.Configuration

import java.nio.file.{Files, Path, Paths}

enum StoreConfig {
  case LocalStoreConfig(local: StoreConfig.LocalStoreLocation = DiskLocation(StoreConfig.DEFAULT_WORKDIR))
      extends StoreConfig
  case DistributedStoreConfig(
      searcher: StoreConfig.LocalStoreLocation = DiskLocation(StoreConfig.DEFAULT_WORKDIR),
      indexer: StoreConfig.LocalStoreLocation = DiskLocation(StoreConfig.DEFAULT_WORKDIR),
      remote: StoreConfig.BlockStoreLocation
  ) extends StoreConfig
}

object StoreConfig {
  lazy val DEFAULT_WORKDIR = Paths.get(System.getProperty("user.dir"), "indexes")

  def apply() = StoreConfig.LocalStoreConfig()

  enum LocalStoreLocation {
    case DiskLocation(path: Path = StoreConfig.DEFAULT_WORKDIR) extends LocalStoreLocation
    case MemoryLocation()                                       extends LocalStoreLocation
  }

  enum BlockStoreLocation {
    case S3Location(bucket: String, prefix: String, region: Option[String] = None, endpoint: Option[String] = None)
        extends BlockStoreLocation
    case RemoteDiskLocation(path: Path) extends BlockStoreLocation
  }

  object yaml {
    given diskLocationDecoder: Decoder[LocalStoreLocation.DiskLocation] = Decoder.instance(c =>
      for {
        path <- c.downField("path").as[Option[Path]]
      } yield {
        DiskLocation(path.getOrElse(DEFAULT_WORKDIR))
      }
    )

    given memoryLocationDecoder: Decoder[LocalStoreLocation.MemoryLocation] =
      Decoder.instance(c => Right(MemoryLocation()))

    given localStoreLocationDecoder: Decoder[LocalStoreLocation] = Decoder.instance(c =>
      c.downField("disk").focus match {
        case Some(disk) => diskLocationDecoder.decodeJson(disk)
        case None =>
          c.downField("memory").focus match {
            case Some(memory) => memoryLocationDecoder.decodeJson(memory)
            case None =>
              Left(
                DecodingFailure(s"cannot decode LocalStoreLocation: expected disk/memory, but got ${c.keys}", c.history)
              )
          }
      }
    )
    given localStoreConfigDecoder: Decoder[LocalStoreConfig] =
      Decoder.instance(c => c.as[LocalStoreLocation].map(c => LocalStoreConfig(c)))

    given s3LocationDecoder: Decoder[BlockStoreLocation.S3Location] = Decoder.instance(c =>
      for {
        bucket   <- c.downField("bucket").as[String]
        prefix   <- c.downField("prefix").as[String]
        region   <- c.downField("region").as[Option[String]]
        endpoint <- c.downField("endpoint").as[Option[String]]
      } yield {
        S3Location(bucket, prefix, region, endpoint)
      }
    )
    given remoteDiskLocationDecoder: Decoder[BlockStoreLocation.RemoteDiskLocation] = deriveDecoder
    given blockStoreLocationDecoder: Decoder[BlockStoreLocation] = Decoder.instance(c =>
      c.downField("s3").focus match {
        case Some(s3) => s3LocationDecoder.decodeJson(s3)
        case None =>
          c.downField("disk").focus match {
            case Some(disk) => remoteDiskLocationDecoder.decodeJson(disk)
            case None =>
              Left(DecodingFailure(s"cannot decode BlockStoreLocation: expected s3/disk, got ${c.keys}", c.history))
          }
      }
    )
    given distributedStoreConfigDecoder: Decoder[DistributedStoreConfig] = Decoder.instance(c =>
      for {
        searcher <- c.downField("searcher").as[Option[LocalStoreLocation]]
        indexer  <- c.downField("indexer").as[Option[LocalStoreLocation]]
        remote   <- c.downField("remote").as[BlockStoreLocation]
      } yield {
        DistributedStoreConfig(
          searcher = searcher.getOrElse(DiskLocation()),
          indexer = indexer.getOrElse(DiskLocation()),
          remote = remote
        )
      }
    )

    given storeConfigDecoder: Decoder[StoreConfig] = Decoder.instance(c =>
      c.downField("local").focus match {
        case Some(local) => localStoreConfigDecoder.decodeJson(local)
        case None =>
          c.downField("distributed").focus match {
            case Some(dist) => distributedStoreConfigDecoder.decodeJson(dist)
            case None =>
              Left(DecodingFailure(s"cannot decode StoreConfig: expected local/distributed, got ${c.keys}", c.history))
          }
      }
    )

  }

  object json {
    given localStoreConfigDecoder: Decoder[LocalStoreConfig]             = deriveDecoder
    given distributedStoreConfigDecoder: Decoder[DistributedStoreConfig] = deriveDecoder
    given storeConfigDecoder: Decoder[StoreConfig] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(err)            => Left(err)
        case Right("local")       => localStoreConfigDecoder.tryDecode(c)
        case Right("distributed") => distributedStoreConfigDecoder.tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"cannot decode StoreConfig: expected local/distributed, got '$other'", c.history))
      }
    )

    given localStoreConfigEncoder: Encoder[LocalStoreConfig]             = deriveEncoder
    given distributedStoreConfigEncoder: Encoder[DistributedStoreConfig] = deriveEncoder
    given storeConfigEncoder: Encoder[StoreConfig] = Encoder.instance {
      case s: StoreConfig.LocalStoreConfig       => localStoreConfigEncoder(s).withType("local")
      case s: StoreConfig.DistributedStoreConfig => distributedStoreConfigEncoder(s).withType("distributed")
    }

    given diskLocationDecoder: Decoder[LocalStoreLocation.DiskLocation]     = deriveDecoder
    given memoryLocationDecoder: Decoder[LocalStoreLocation.MemoryLocation] = deriveDecoder
    given localStoreLocationDecoder: Decoder[LocalStoreLocation] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(err)       => Left(err)
        case Right("memory") => memoryLocationDecoder.tryDecode(c)
        case Right("disk")   => diskLocationDecoder.tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"cannot decode LocalStoreLocation: expected disk/memory, got '$other'", c.history))
      }
    )

    given diskLocationEncoder: Encoder[LocalStoreLocation.DiskLocation]     = deriveEncoder
    given memoryLocationEncoder: Encoder[LocalStoreLocation.MemoryLocation] = deriveEncoder
    given localStoreLocationEncoder: Encoder[LocalStoreLocation] = Encoder.instance {
      case s: LocalStoreLocation.DiskLocation   => diskLocationEncoder(s).withType("disk")
      case s: LocalStoreLocation.MemoryLocation => memoryLocationEncoder(s).withType("memory")
    }

    given s3LocationDecoder: Decoder[BlockStoreLocation.S3Location]                 = deriveDecoder
    given remoteDiskLocationDecoder: Decoder[BlockStoreLocation.RemoteDiskLocation] = deriveDecoder
    given blockStoreLocationDecoder: Decoder[BlockStoreLocation] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(err)     => Left(err)
        case Right("s3")   => s3LocationDecoder.tryDecode(c)
        case Right("disk") => remoteDiskLocationDecoder.tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"cannot decode BlockStoreLocation: expected disk/s3, got '$other'", c.history))
      }
    )

    given s3LocationEncoder: Encoder[BlockStoreLocation.S3Location]                 = deriveEncoder
    given remoteDiskLocationEncoder: Encoder[BlockStoreLocation.RemoteDiskLocation] = deriveEncoder
    given blockStoreLocationEncoder: Encoder[BlockStoreLocation] = Encoder.instance {
      case s: BlockStoreLocation.S3Location         => s3LocationEncoder(s).withType("s3")
      case s: BlockStoreLocation.RemoteDiskLocation => remoteDiskLocationEncoder(s).withType("disk")
    }

    extension (json: Json) {
      def withType(tpe: String) = json.deepMerge(Json.fromJsonObject(JsonObject("type" -> Json.fromString(tpe))))
    }
  }

}
