package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticSimpleParams}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.field.{TextField, TextListField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.util.DocumentEmbedder.EmbedTarget
import cats.effect.IO
import fs2.Stream
import cats.syntax.all.*

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import fs2.Stream

case class DocumentEmbedder(
    mapping: IndexMapping,
    models: EmbedModelDict,
    cache: mutable.Map[String, Option[ModelRef]] = mutable.Map()
) extends Logging {
  def embed(docs: List[Document]): IO[List[Document]] = for {
    targets    <- selectEmbedStrings(docs).compile.toList
    embedCache <- embedStrings(targets)
    result     <- join(docs, embedCache)
  } yield {
    result
  }

  def join(docs: List[Document], embeds: Map[EmbedTarget, Array[Float]]): IO[List[Document]] = IO {
    docs.map(doc =>
      doc.copy(fields = doc.fields.map {
        case field: TextLikeField =>
          cache.get(field.name) match {
            case Some(Some(model)) =>
              field match {
                case text: TextField if text.embedding.isDefined =>
                  // fast path for pre-embedded docs
                  text
                case text: TextField =>
                  embeds.get(EmbedTarget(model, text.value)) match {
                    case Some(embed) => text.copy(embedding = Some(embed))
                    case None        =>
                      throw BackendError(
                        s"field ${field.name} should have an embedding for text '${text.value}', but it's not - this is a bug"
                      )
                  }
                case list: TextListField if list.embeddings.isDefined =>
                  // fast path for pre-embedded docs
                  list
                case list: TextListField =>
                  list.copy(embeddings =
                    Some(
                      list.value.map(text =>
                        embeds.get(EmbedTarget(model, text)) match {
                          case Some(embed) => embed
                          case None        =>
                            throw BackendError(
                              s"field ${field.name} should have an embedding for '${text}', but it's not - this is a bug"
                            )
                        }
                      )
                    )
                  )
                case other => other
              }
            case _ => field
          }
        case other => other // skip
      })
    )
  }

  def embedStrings(targets: List[EmbedTarget]): IO[Map[EmbedTarget, Array[Float]]] = for {
    sorted     <- IO(targets.groupBy(_.model).map { case (model, docs) => model -> docs.sortBy(_.text.length) })
    embeddings <- Stream
      .emits(sorted.toList)
      .evalMap((model, docs) =>
        models
          .encode(model, TaskType.Document, docs.map(_.text))
          .map(embeds => embeds.zip(docs).map((emb, doc) => doc -> emb))
      )
      .flatMap(batch => Stream.emits(batch))
      .compile
      .toList

  } yield {
    embeddings.toMap
  }

  def selectEmbedStrings(docs: List[Document]): Stream[IO, EmbedTarget] = for {
    doc       <- Stream.emits(docs)
    textField <- Stream.emits(doc.fields).collect { case text: TextLikeField => text }
    target    <- cache.get(textField.name) match {
      // fast path, should be embedded
      case Some(Some(model)) =>
        textField match {
          case TextField(name, value, None)      => Stream.emit(EmbedTarget(model, value))
          case TextListField(name, values, None) =>
            Stream.emits(values.map(value => EmbedTarget(model, value)))
          case _ => Stream.empty
        }
      // fast path, no embedding
      case Some(None) => Stream.empty
      case None       =>
        mapping.fieldSchemaOf[TextLikeFieldSchema[?]](textField.name) match {
          case Some(schema) =>
            schema.search.semantic match {
              case Some(SemanticInferenceParams(model = model)) =>
                cache.put(textField.name, Some(model))
                textField match {
                  case TextField(_, value, None)      => Stream.emit(EmbedTarget(model, value))
                  case TextListField(_, values, None) => Stream.emits(values.map(EmbedTarget(model, _)))
                  case _                              => Stream.empty
                }

              case Some(_: SemanticSimpleParams) =>
                textField match {
                  case TextField(_, _, None) | TextListField(_, _, None) =>
                    Stream.raiseError[IO](
                      UserError(
                        s"field ${textField.name} has no embedding model defined for local inference, but doc $doc has it without an explicit embedding vector."
                      )
                    )
                  case _ => Stream.empty
                }
              case None =>
                cache.put(textField.name, None)
                Stream.empty
            }
          case None => Stream.empty
        }
    }
  } yield {
    target
  }

}

object DocumentEmbedder {
  case class EmbedTarget(model: ModelRef, text: String)

}
