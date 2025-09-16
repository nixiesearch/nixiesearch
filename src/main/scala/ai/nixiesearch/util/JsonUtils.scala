package ai.nixiesearch.util

import io.circe.{Decoder, DecodingFailure, HCursor, Json}

import scala.compiletime.{constValueTuple, constValue}
import scala.deriving.Mirror

object JsonUtils {

  inline def forbidExtraFields[T](json: Json)(using m: Mirror.ProductOf[T]): Decoder.Result[Unit] = {
    val labels = constValueTuple[m.MirroredElemLabels].toList
    val name   = constValue[m.MirroredLabel]
    print(labels)
    json.asObject match {
      case Some(value) =>
        value.keys.find(jsonKey => !labels.contains(jsonKey)) match {
          case Some(value) =>
            Left(DecodingFailure(s"${name} expects only fields ${labels} but got field '${value}'", Nil))
          case None => Right({})
        }
      case None => Right({})
    }

  }
}
