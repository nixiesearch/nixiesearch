package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.{Document, Field, Logging}
import io.circe.{ACursor, Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import cats.implicits.*

import scala.util.{Failure, Success}
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.SearchType.LexicalSearch
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import ai.nixiesearch.core.Field.*
import cats.effect.IO

case class IndexMapping(name: String, alias: List[Alias] = Nil, fields: Map[String, FieldSchema[_ <: Field]])
    extends Logging {
  val intFields      = fields.collect { case (name, s: IntFieldSchema) => name -> s }
  val textFields     = fields.collect { case (name, s: TextFieldSchema) => name -> s }
  val textListFields = fields.collect { case (name, s: TextListFieldSchema) => name -> s }

  def migrate(updated: IndexMapping): IO[IndexMapping] = for {
    fieldNames <- IO(fields.keySet ++ updated.fields.keySet)
    migrated <- fieldNames.toList.traverse(field => ensureFieldMigratable(fields.get(field), updated.fields.get(field)))
    _        <- IO.whenA(this != updated)(info(s"migration of changed index mapping '$name' is successful"))
  } yield {
    updated
  }

  def ensureFieldMigratable(
      before: Option[FieldSchema[_ <: Field]],
      after: Option[FieldSchema[_ <: Field]]
  ): IO[Unit] = (before, after) match {
    case (Some(deleted), None) => info(s"field ${deleted.name} removed from the index $name mapping")
    case (None, Some(added))   => info(s"field ${added.name} added to the index $name mapping")
    case (Some(a: IntFieldSchema), Some(b: IntFieldSchema))           => IO.unit
    case (Some(a: TextFieldSchema), Some(b: TextFieldSchema))         => IO.unit
    case (Some(a: TextListFieldSchema), Some(b: TextListFieldSchema)) => IO.unit
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
  case class Alias(name: String)

  def apply(name: String, fields: List[FieldSchema[_ <: Field]]): IndexMapping = {
    new IndexMapping(name, fields = fields.map(f => f.name -> f).toMap)
  }

  def fromDocument(docs: List[Document], indexName: String): IO[IndexMapping] = for {
    fieldValues1 <- IO(docs.flatMap(_.fields).groupBy(_.name).toList)
    fieldValues <- fieldValues1.flatTraverse {
      case (fieldName, values @ head :: _) =>
        head match {
          case f: TextField =>
            info(s"detected field '$fieldName' of type text: search=lexical sort=true facet=true, filter=true") *>
              IO.pure(List(TextFieldSchema.dynamicDefault(fieldName)))
          case f: TextListField =>
            info(s"detected field '$fieldName' of type text[]: search=lexical sort=true facet=true, filter=true") *>
              IO.pure(List(TextListFieldSchema.dynamicDefault(fieldName)))
          case f: IntField =>
            info(s"detected field '$fieldName' of type int: sort=true facet=true, filter=true") *>
              IO.pure(List(IntFieldSchema.dynamicDefault(fieldName)))
        }
      case (fieldName, _) => IO(List.empty[FieldSchema[_ <: Field]]) // should never happen
    }
  } yield {
    IndexMapping(indexName, fieldValues)
  }

  object Alias {
    implicit val aliasDecoder: Decoder[Alias] = Decoder.decodeString.emapTry {
      case ""    => Failure(new Exception("index alias cannot be empty"))
      case other => Success(Alias(other))
    }
    implicit val aliasEncoder: Encoder[Alias] = Encoder.encodeString.contramap(_.name)
  }

  object yaml {
    def indexMappingDecoder(name: String): Decoder[IndexMapping] = Decoder.instance(c =>
      for {
        alias      <- decodeAlias(c.downField("alias"))
        fieldJsons <- c.downField("fields").as[Map[String, Json]].map(_.toList)
        fields <- fieldJsons.traverse { case (name, json) =>
          FieldSchema.yaml.fieldSchemaDecoder(name).decodeJson(json)
        }
      } yield {
        IndexMapping(name, alias, fields.map(f => f.name -> f).toMap)
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
    import FieldSchema.json._
    implicit val indexMappingDecoder: Decoder[IndexMapping] = deriveDecoder
    implicit val indexMappingEncoder: Encoder[IndexMapping] = deriveEncoder
  }

}
