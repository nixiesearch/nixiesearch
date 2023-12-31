package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.{Document, Field, Logging}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*
import cats.implicits.*

import scala.util.{Failure, Success}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.config.mapping.IndexMapping.Migration.*
import ai.nixiesearch.config.mapping.IndexMapping.{Alias, Migration}
import ai.nixiesearch.core.Field.*
import cats.effect.{IO, Ref}

case class IndexMapping(
    name: String,
    alias: List[Alias] = Nil,
    config: IndexConfig = IndexConfig(),
    fields: Map[String, FieldSchema[_ <: Field]]
) extends Logging {
  val intFields      = fields.collect { case (name, s: IntFieldSchema) => name -> s }
  val longFields     = fields.collect { case (name, s: LongFieldSchema) => name -> s }
  val floatFields    = fields.collect { case (name, s: FloatFieldSchema) => name -> s }
  val doubleFields   = fields.collect { case (name, s: DoubleFieldSchema) => name -> s }
  val textFields     = fields.collect { case (name, s: TextFieldSchema) => name -> s }
  val textListFields = fields.collect { case (name, s: TextListFieldSchema) => name -> s }

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

  def withDynamicMapping(enabled: Boolean): IndexMapping = {
    copy(config = config.copy(mapping = config.mapping.copy(dynamic = enabled)))
  }

  def dynamic(updated: IndexMapping): IO[IndexMapping] = for {
    fieldNames <- IO(fields.keySet ++ updated.fields.keySet)
    migrations <- fieldNames.toList.traverse(field => migrateField(fields.get(field), updated.fields.get(field)))
    _ <- migrations.traverse {
      case Add(field) => info(s"field ${field.name} added to the index $name mapping")
      case _          => IO.unit
    }
    _ <- IO.whenA(this != updated)(info(s"migration of changed index mapping '$name' is successful"))
    mergedFields <- IO(
      fieldNames.toList
        .flatMap(field => updated.fields.get(field).orElse(fields.get(field)).map(f => f.name -> f))
        .toMap
    )
  } yield {
    IndexMapping(
      name = updated.name,
      alias = updated.alias,
      config = updated.config,
      fields = mergedFields
    )
  }

  def migrateField(before: Option[FieldSchema[_ <: Field]], after: Option[FieldSchema[_ <: Field]]): IO[Migration] =
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

}

object IndexMapping extends Logging {
  sealed trait Migration {
    def field: FieldSchema[_ <: Field]
  }
  object Migration {
    case class Add(field: FieldSchema[_ <: Field])    extends Migration
    case class Delete(field: FieldSchema[_ <: Field]) extends Migration
    case class Keep(field: FieldSchema[_ <: Field])   extends Migration
  }

  case class Alias(name: String)

  def apply(name: String, fields: List[FieldSchema[_ <: Field]]): IndexMapping = {
    new IndexMapping(name, fields = fields.map(f => f.name -> f).toMap, config = IndexConfig())
  }

  def fromDocument(docs: List[Document], indexName: String): IO[IndexMapping] = for {
    fieldValues1 <- IO(docs.flatMap(_.fields).groupBy(_.name).toList)
    fieldValues <- fieldValues1.flatTraverse {
      case (fieldName, values @ head :: _) =>
        head match {
          case f: TextField if fieldName == "_id" => IO.pure(List(TextFieldSchema.idDefault()))
          case f: TextField                       => IO.pure(List(TextFieldSchema.dynamicDefault(fieldName)))
          case f: TextListField                   => IO.pure(List(TextListFieldSchema.dynamicDefault(fieldName)))
          case f: IntField                        => IO.pure(List(IntFieldSchema.dynamicDefault(fieldName)))
          case f: LongField                       => IO.pure(List(LongFieldSchema.dynamicDefault(fieldName)))
          case f: FloatField                      => IO.pure(List(FloatFieldSchema.dynamicDefault(fieldName)))
          case f: DoubleField                     => IO.pure(List(DoubleFieldSchema.dynamicDefault(fieldName)))
        }
      case (fieldName, _) => IO(List.empty[FieldSchema[_ <: Field]]) // should never happen
    }
  } yield {
    val withId =
      if (fieldValues.exists(_.name == "_id")) fieldValues
      else fieldValues :+ TextFieldSchema.idDefault()
    IndexMapping(indexName, withId)
  }

  object Alias {
    given aliasDecoder: Decoder[Alias] = Decoder.decodeString.emapTry {
      case ""    => Failure(new Exception("index alias cannot be empty"))
      case other => Success(Alias(other))
    }
    given aliasEncoder: Encoder[Alias] = Encoder.encodeString.contramap(_.name)
  }

  object yaml {
    def indexMappingDecoder(name: String): Decoder[IndexMapping] = Decoder.instance(c =>
      for {
        alias <- decodeAlias(c.downField("alias"))
        fieldJsons <- c.downField("fields").as[Map[String, Json]].map(_.toList) match {
          case Left(value)  => Left(DecodingFailure(s"'fields' expected to be a map", c.history))
          case Right(value) => Right(value)
        }
        fields <- fieldJsons.traverse { case (name, json) =>
          FieldSchema.yaml.fieldSchemaDecoder(name).decodeJson(json)
        }

        config <- c.downField("config").as[Option[IndexConfig]].map(_.getOrElse(IndexConfig()))
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
        IndexMapping(name, alias = alias, fields = extendedFields, config = config)

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
    given indexMappingDecoder: Decoder[IndexMapping] = deriveDecoder
    given indexMappingEncoder: Encoder[IndexMapping] = deriveEncoder
  }

}
