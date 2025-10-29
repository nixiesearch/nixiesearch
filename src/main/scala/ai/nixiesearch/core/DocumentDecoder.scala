package ai.nixiesearch.core

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.DocumentDecoder.JsonError
import io.circe.DecodingFailure

import scala.annotation.tailrec
import scala.collection.mutable

case class DocumentDecoder(mapping: IndexMapping) {
  @tailrec final def decodeObj(
      input: String,
      i: Int,
      fields: mutable.ArrayBuffer[Field] = mutable.ArrayBuffer.empty
  ): Either[JsonError, Document] = {
    input.charAt(i) match {
      case '"' =>
        val nameEnd = DocumentDecoder.decodeString(input, i + 1)
        if (nameEnd != -1) {
          val name = input.substring(i, nameEnd)
        }
      case ' ' | '\n' | '\t' => decodeObj(input, i + 1, fields)
    }
  }

}

object DocumentDecoder {
  case class JsonError(msg: String) extends Throwable(msg)

  final def decodeString(input: String, i: Int): Int = {
    var j          = i
    var quoteFound = false
    while (!quoteFound && (j < input.length)) {
      if (input.charAt(j) == '"') {
        if (input.charAt(j - 1) != '\\') {
          quoteFound = true
        } else {
          j += 1
        }
      } else {
        j += 1
      }
    }
    if (quoteFound) {
      j
    } else {
      -1
    }
  }

}
