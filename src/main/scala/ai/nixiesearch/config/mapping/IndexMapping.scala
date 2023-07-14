package ai.nixiesearch.config

import ai.nixiesearch.config.IndexMapping.Alias
import ai.nixiesearch.core.Field
import io.circe.{ACursor, Decoder, Json, Encoder}
import io.circe.generic.semiauto._
import cats.implicits.*

import scala.util.{Failure, Success}
import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.FieldSchema.TextListFieldSchema

case class IndexMapping(name: String, alias: List[Alias] = Nil, fields: Map[String, FieldSchema[_ <: Field]]) {
  val intFields      = fields.collect { case (name, s: IntFieldSchema) => name -> s }
  val textFields     = fields.collect { case (name, s: TextFieldSchema) => name -> s }
  val textListFields = fields.collect { case (name, s: TextListFieldSchema) => name -> s }
}

object IndexMapping {
  case class Alias(name: String)

  def apply(name: String, fields: List[FieldSchema[_ <: Field]]): IndexMapping = {
    new IndexMapping(name, fields = fields.map(f => f.name -> f).toMap)
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
