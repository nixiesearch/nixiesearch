package ai.nixiesearch.util.analytics

import ai.nixiesearch.config.{Config, StoreConfig}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.{
  BlockStoreLocation,
  DistributedStoreConfig,
  LocalStoreConfig,
  LocalStoreLocation
}
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.*
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticParams, SemanticSimpleParams}
import ai.nixiesearch.config.mapping.{FieldName, IndexName, SearchParams}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.main.Logo
import ai.nixiesearch.util.analytics.OnStartAnalyticsPayload.SystemParams
import buildinfo.BuildInfo
import cats.effect.IO
import io.circe.Codec
import org.apache.commons.codec.digest.DigestUtils

import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.http4s.EntityEncoder
import org.http4s.circe.*

import java.nio.file.{Path, Paths}

case class OnStartAnalyticsPayload(
    config: Config,
    system: SystemParams,
    confHash: String,
    macHash: Option[String],
    version: String,
    mode: String
)

object OnStartAnalyticsPayload {
  case class SystemParams(os: String, arch: String, jvm: String, args: String)

  object SystemParams {
    def create(): IO[SystemParams] = for {
      os   <- prop("os.name")
      arch <- prop("os.arch")
      jvm  <- prop("java.version")
    } yield {
      SystemParams(os, arch, jvm, args = Logo.args)
    }

    def prop(name: String): IO[String] =
      IO.fromOption(Option(System.getProperty(name)))(BackendError(s"cannot get property $name"))

  }

  import Config.given
  given systemCodec: Codec[SystemParams]                        = deriveCodec
  given payloadCodec: Codec[OnStartAnalyticsPayload]            = deriveCodec
  given payloadJson: EntityEncoder[IO, OnStartAnalyticsPayload] = jsonEncoderOf

  def create(config: Config, mode: String): IO[OnStartAnalyticsPayload] = for {
    conf     <- IO(anonymizeConfig(config))
    system   <- SystemParams.create()
    confHash <- IO(hash(config.asJson.noSpaces))
    macHash  <- getMacHash
  } yield {
    OnStartAnalyticsPayload(
      config = conf,
      system = system,
      confHash = confHash,
      macHash = macHash,
      version = BuildInfo.version,
      mode = mode
    )
  }

  def getMacHash: IO[Option[String]] = IO {
    val interfaces = NetworkInterface
      .networkInterfaces()
      .collect(Collectors.toList[NetworkInterface])
      .asScala
      .filter(iface => iface.isUp && !iface.isLoopback && !iface.isVirtual)
      .flatMap(iface => Option(iface.getHardwareAddress))

    interfaces.headOption.map(addr => DigestUtils.sha256Hex(addr))
  }

  private def anonymizeConfig(config: Config): Config = config.copy(
    core = config.core.copy(cache = config.core.cache.copy(dir = hash(config.core.cache.dir))),
    inference = config.inference.copy(
      embedding = config.inference.embedding.map { case (ref, conf) =>
        ModelRef(hash(ref.name)) -> conf
      },
      completion = config.inference.completion.map { case (ref, conf) =>
        ModelRef(hash(ref.name)) -> conf
      }
    ),
    schema = config.schema.map { case (indexName, mapping) =>
      IndexName(hash(indexName.value)) -> mapping.copy(
        name = IndexName(hash(mapping.name.value)),
        store = mapping.store match {
          case s: DistributedStoreConfig =>
            s.copy(searcher = hash(s.searcher), indexer = hash(s.indexer), remote = hash(s.remote))
          case s: LocalStoreConfig => s.copy(local = hash(s.local))
        },
        alias = mapping.alias.map(alias => Alias(hash(alias.name))),
        fields = mapping.fields.map {
          case (name, value) => {
            val anonName: FieldName = name match {
              case FieldName.StringName(name)                   => StringName(hash(name))
              case FieldName.WildcardName(name, prefix, suffix) => WildcardName(hash(name), hash(prefix), hash(suffix))
            }
            anonName -> (value match {
              case s: IntFieldSchema      => s.copy(name = anonName)
              case s: FloatFieldSchema    => s.copy(name = anonName)
              case s: LongFieldSchema     => s.copy(name = anonName)
              case s: DoubleFieldSchema   => s.copy(name = anonName)
              case s: TextFieldSchema     => s.copy(name = anonName)
              case s: TextListFieldSchema => s.copy(name = anonName)
              case s: BooleanFieldSchema  => s.copy(name = anonName)
              case s: GeopointFieldSchema => s.copy(name = anonName)
              case s: DateFieldSchema     => s.copy(name = anonName)
              case s: DateTimeFieldSchema => s.copy(name = anonName)
            })
          }
        }
      )
    }
  )

  def hash(value: String): String = DigestUtils.md5Hex(value)
  def hash(value: Path): Path     = Paths.get(hash(value.toString))

  def hash(search: SearchParams): SearchParams = search.copy(
    semantic = search.semantic.map(s => hash(s))
  )

  def hash(sem: SemanticParams): SemanticParams = sem match {
    case s: SemanticInferenceParams => s.copy(model = ModelRef(hash(s.model.name)))
    case s: SemanticSimpleParams    => s
  }

  def hash(loc: LocalStoreLocation): LocalStoreLocation = loc match {
    case DiskLocation(path) => DiskLocation(hash(path))
    case MemoryLocation()   => MemoryLocation()
  }

  def hash(loc: BlockStoreLocation): BlockStoreLocation = loc match {
    case S3Location(bucket, prefix, region, endpoint) => S3Location(hash(bucket), hash(prefix), region, endpoint)
    case RemoteDiskLocation(path)                     => RemoteDiskLocation(hash(path))
  }

}
