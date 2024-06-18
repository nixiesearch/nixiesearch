package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.{CacheConfig, FieldSchema, StoreConfig}
import ai.nixiesearch.core.{Document, Field, Logging}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*
import cats.implicits.*

import scala.util.{Failure, Success}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, SemanticSearchLikeType}
import ai.nixiesearch.config.mapping.IndexMapping.Migration.*
import ai.nixiesearch.config.mapping.IndexMapping.{Alias, Migration}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.kernel.Resource
import cats.effect.{IO, Ref}
import org.apache.lucene.store.{Directory, IOContext}
import io.circe.parser.*
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper

import scala.jdk.CollectionConverters.*

case class IndexMapping(
    name: IndexName,
    alias: List[Alias] = Nil,
    config: IndexConfig = IndexConfig(),
    store: StoreConfig = StoreConfig(),
    cache: CacheConfig = CacheConfig(),
    fields: Map[String, FieldSchema[? <: Field]]
) extends Logging {
  val intFields      = fields.collect { case (name, s: IntFieldSchema) => name -> s }
  val longFields     = fields.collect { case (name, s: LongFieldSchema) => name -> s }
  val floatFields    = fields.collect { case (name, s: FloatFieldSchema) => name -> s }
  val doubleFields   = fields.collect { case (name, s: DoubleFieldSchema) => name -> s }
  val textFields     = fields.collect { case (name, s: TextFieldSchema) => name -> s }
  val textListFields = fields.collect { case (name, s: TextListFieldSchema) => name -> s }
  val booleanFields  = fields.collect { case (name, s: BooleanFieldSchema) => name -> s }

  val analyzer = IndexMapping.createAnalyzer(this)

  def migrate(updated: IndexMapping): IO[IndexMapping] = for {
    fieldNames <- IO(fields.keySet ++ updated.fields.keySet)
    migrations <- fieldNames.toList.traverse(field => migrateField(fields.get(field), updated.fields.get(field)))
    _ <- migrations.traverse {
      case Add(field)    => info(s"field ${field.name} added to the index $name mapping")
      case Delete(field) => info(s"field ${field.name} not present in the index $name mapping")
      case _             => IO.unit
    }
    _ <- IO.whenA(this != updated)(info(s"migration of changed index mapping '$name' is successful"))
  } yield {
    updated
  }

  def migrateField(before: Option[FieldSchema[? <: Field]], after: Option[FieldSchema[? <: Field]]): IO[Migration] =
    (before, after) match {
      case (Some(deleted), None)                                        => IO.pure(Delete(deleted))
      case (None, Some(added))                                          => IO.pure(Add(added))
      case (Some(a: IntFieldSchema), Some(b: IntFieldSchema))           => IO.pure(Keep(b))
      case (Some(a: LongFieldSchema), Some(b: LongFieldSchema))         => IO.pure(Keep(b))
      case (Some(a: TextFieldSchema), Some(b: TextFieldSchema))         => IO.pure(Keep(b))
      case (Some(a: TextListFieldSchema), Some(b: TextListFieldSchema)) => IO.pure(Keep(b))
      case (Some(a), Some(b)) if a == b                                 => IO.pure(Keep(b))
      case (Some(a), Some(b)) => IO.raiseError(new Exception(s"cannot migrate field schema $a to $b"))
      case (None, None) =>
        IO.raiseError(
          new Exception(
            s"You've found a bug, congratulations! Tried to migrate a non-existent field to another non-existent field, which cannot happen"
          )
        )
    }
  def modelHandles(): List[ModelHandle] =
    fields.values.toList.collect { case TextLikeFieldSchema(_, SemanticSearchLikeType(model, _), _, _, _, _, _, _) =>
      model
    }

  def suggestFields(): List[String] =
    fields.values.toList.collect { case TextLikeFieldSchema(name, _, _, _, _, _, _, Some(_)) =>
      name
    }
}

object IndexMapping extends Logging {
  sealed trait Migration {
    def field: FieldSchema[? <: Field]
  }
  object Migration {
    case class Add(field: FieldSchema[? <: Field])    extends Migration
    case class Delete(field: FieldSchema[? <: Field]) extends Migration
    case class Keep(field: FieldSchema[? <: Field])   extends Migration
  }

  case class Alias(name: String)

  def apply(
      name: IndexName,
      fields: List[FieldSchema[? <: Field]],
      store: StoreConfig
  ): IndexMapping = {
    new IndexMapping(
      name,
      fields = fields.map(f => f.name -> f).toMap,
      config = IndexConfig(),
      store = store
    )
  }

  def createAnalyzer(mapping: IndexMapping): Analyzer = {
    val fieldAnalyzers = mapping.fields.values.collect { case TextLikeFieldSchema(name, _, _, _, _, _, language, _) =>
      name -> language.analyzer
    }
    new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldAnalyzers.toMap.asJava)
  }

  object Alias {
    given aliasDecoder: Decoder[Alias] = Decoder.decodeString.emapTry {
      case ""    => Failure(new Exception("index alias cannot be empty"))
      case other => Success(Alias(other))
    }
    given aliasEncoder: Encoder[Alias] = Encoder.encodeString.contramap(_.name)
  }

  object yaml {
    import StoreConfig.yaml.given

    def indexMappingDecoder(name: IndexName): Decoder[IndexMapping] = Decoder.instance(c =>
      for {
        alias <- decodeAlias(c.downField("alias"))
        fieldJsons <- c.downField("fields").as[Map[String, Json]].map(_.toList) match {
          case Left(value)  => Left(DecodingFailure(s"'fields' expected to be a map: $value", c.history))
          case Right(value) => Right(value)
        }
        fields <- fieldJsons.traverse { case (name, json) =>
          FieldSchema.yaml.fieldSchemaDecoder(name).decodeJson(json)
        }
        store  <- c.downField("store").as[Option[StoreConfig]].map(_.getOrElse(StoreConfig()))
        config <- c.downField("config").as[Option[IndexConfig]].map(_.getOrElse(IndexConfig()))
        cache  <- c.downField("cache").as[Option[CacheConfig]].map(_.getOrElse(CacheConfig()))
      } yield {
        val fieldsMap = fields.map(f => f.name -> f).toMap
        val extendedFields = fieldsMap.get("_id") match {
          case Some(idMapping) =>
            logger.warn("_id field is internal field and it's mapping cannot be changed")
            logger.warn("_id field mapping ignored. Default mapping: search=false facet=false sort=false filter=true")
            fieldsMap.updated("_id", TextFieldSchema("_id", filter = true))
          case None =>
            fieldsMap.updated("_id", TextFieldSchema("_id", filter = true))
        }
        IndexMapping(name, alias = alias, fields = extendedFields, config = config, store = store, cache = cache)

      }
    )

    def decodeAlias(c: ACursor): Decoder.Result[List[Alias]] = {
      c.as[Option[Alias]] match {
        case Left(value)        => c.as[List[Alias]]
        case Right(Some(value)) => Right(List(value))
        case Right(None)        => Right(Nil)
      }
    }
  }

  object json {
    import FieldSchema.json.given
    import SearchType.json.given
    import ai.nixiesearch.util.PathJson.given
    import ai.nixiesearch.config.StoreConfig.json.given
    given indexMappingDecoder: Decoder[IndexMapping] = deriveDecoder
    given indexMappingEncoder: Encoder[IndexMapping] = deriveEncoder
  }

}
