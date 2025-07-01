package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.{EmbedCacheConfig, FieldSchema, StoreConfig}
import ai.nixiesearch.core.{Field, Logging}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*
import cats.syntax.all.*

import scala.util.{Failure, Success}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName, fieldNameDecoder}
import ai.nixiesearch.config.mapping.IndexMapping.Migration.*
import ai.nixiesearch.config.mapping.IndexMapping.{Alias, Migration}
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper

import scala.jdk.CollectionConverters.*
import language.experimental.namedTuples

case class IndexMapping(
    name: IndexName,
    alias: List[Alias] = Nil,
    config: IndexConfig = IndexConfig(),
    store: StoreConfig = StoreConfig(),
    fields: Map[FieldName, FieldSchema[? <: Field]]
) extends Logging {

  val analyzer                                      = PerFieldAnalyzer(new KeywordAnalyzer(), this)
  lazy val requiredFields: List[FieldName]          = fields.filter(_._2.required).keys.toList
  lazy val textFields: List[TextLikeFieldSchema[?]] = fields.values.collect { case s: TextLikeFieldSchema[?] =>
    s
  }.toList

  def fieldSchema(name: String): Option[FieldSchema[? <: Field]] = {
    fields.collectFirst {
      case (field, schema) if field.matches(name) => schema
    }
  }

  def fieldSchemaOf[S <: FieldSchema[? <: Field]](
      name: String
  )(using manifest: scala.reflect.ClassTag[S]): Option[S] = {
    fields.collectFirst {
      case (field, schema: S) if field.matches(name) => schema
    }
  }

  def nameMatches(value: String): Boolean = {
    (name.value == value) || alias.contains(Alias(value))
  }

  def migrate(updated: IndexMapping): IO[IndexMapping] = for {
    fieldNames <- IO(fields.keySet ++ updated.fields.keySet)
    migrations <- fieldNames.toList.traverse(field => migrateField(fields.get(field), updated.fields.get(field)))
    _          <- migrations.traverse {
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
      case (Some(a), Some(b))                                           =>
        IO.raiseError(new Exception(s"cannot migrate field schema $a to $b"))
      case (None, None) =>
        IO.raiseError(
          new Exception(
            s"You've found a bug, congratulations! Tried to migrate a non-existent field to another non-existent field, which cannot happen"
          )
        )
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
        alias      <- decodeAlias(c.downField("alias"))
        fieldJsons <- c.downField("fields").as[Map[FieldName, Json]].map(_.toList) match {
          case Left(value)  => Left(DecodingFailure(s"'fields' expected to be a map: $value", c.history))
          case Right(value) => Right(value)
        }
        fields <- fieldJsons.traverse { case (name, json) =>
          FieldSchema.yaml.fieldSchemaDecoder(name).decodeJson(json)
        }
        _ <- checkWildcardOverrides(fields) match {
          case Nil      => Right(true)
          case failures =>
            val names = failures.map { case (wc, f) => s"${wc.name}/${f.name}" }
            Left(DecodingFailure(s"Fields $names should not wildcard override each other", c.history))
        }
        store  <- c.downField("store").as[Option[StoreConfig]].map(_.getOrElse(StoreConfig()))
        config <- c.downField("config").as[Option[IndexConfig]].map(_.getOrElse(IndexConfig()))
      } yield {
        val fieldsMap      = fields.map(f => f.name -> f).toMap
        val id             = StringName("_id")
        val extendedFields = fieldsMap.get(id) match {
          case Some(idMapping) =>
            logger.warn("_id field is internal field and it's mapping cannot be changed")
            logger.warn("_id field mapping ignored. Default mapping: search=false facet=false sort=false filter=true")
            fieldsMap.updated(id, TextFieldSchema(id, filter = true))
          case None =>
            fieldsMap.updated(id, TextFieldSchema(id, filter = true))
        }
        IndexMapping(
          name,
          alias = alias,
          fields = extendedFields,
          config = config,
          store = store
        )

      }
    )

    def decodeAlias(c: ACursor): Decoder.Result[List[Alias]] = {
      c.as[Option[Alias]] match {
        case Left(value)        => c.as[List[Alias]]
        case Right(Some(value)) => Right(List(value))
        case Right(None)        => Right(Nil)
      }
    }

    def checkWildcardOverrides(fields: List[FieldSchema[? <: Field]]): List[(WildcardName, StringName)] = {
      val fieldNames = fields.map(_.name)
      for {
        wildcard <- fieldNames.collect { case wc: WildcardName => wc }
        string   <- fieldNames.collect { case s: StringName => s } if wildcard.matches(string.name)
      } yield {
        (wildcard, string)
      }
    }
  }

  object json {
    import FieldSchema.json.given
    import ai.nixiesearch.config.StoreConfig.json.given
    given indexMappingDecoder: Decoder[IndexMapping] = deriveDecoder
    given indexMappingEncoder: Encoder[IndexMapping] = deriveEncoder
  }

}
