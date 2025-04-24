package ai.nixiesearch.util

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.field.{TextField, TextListField}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.util.DocumentEmbedder.EmbedTarget
import cats.effect.IO
import fs2.Stream
import cats.implicits.*

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import fs2.Stream

case class DocumentEmbedder(
    mapping: IndexMapping,
    models: EmbedModelDict,
    cache: mutable.Map[String, Option[ModelRef]] = mutable.Map()
) extends Logging {
  def embed(docs: List[Document]): IO[List[Document]] = for {
    targets    <- selectEmbedStrings(docs)
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
                case text: TextField =>
                  embeds.get(EmbedTarget(model, text.value)) match {
                    case Some(embed) => text.copy(embedding = Some(embed))
                    case None =>
                      throw BackendError(
                        s"field ${field.name} should have an embedding for text '${text.value}', but it's not - this is a bug"
                      )
                  }
                case list: TextListField =>
                  list.copy(embeddings =
                    Some(
                      list.value.map(text =>
                        embeds.get(EmbedTarget(model, text)) match {
                          case Some(embed) => embed
                          case None =>
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
    sorted <- IO(targets.groupBy(_.model).map { case (model, docs) => model -> docs.sortBy(_.text.length) })
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

  def selectEmbedStrings(docs: List[Document]): IO[List[EmbedTarget]] = IO {
    val targets = ArrayBuffer[EmbedTarget]()
    docs.foreach(doc => {
      doc.fields.foreach {
        case text: TextLikeField =>
          cache.get(text.name) match {
            // fast path, should be embedded
            case Some(Some(model)) =>
              text match {
                case TextField(name, value, _) => targets.append(EmbedTarget(model, value))
                case TextListField(name, values, _) =>
                  values.foreach(value => targets.append(EmbedTarget(model, value)))
                case _ => // skip
              }
            // fast path, no embedding
            case Some(None) => // skip
            // slow path
            case None =>
              mapping
                .fieldSchemaOf[TextLikeFieldSchema[?]](text.name)
                .foreach(schema => {
                  schema.search.semantic match {
                    case None => cache.put(text.name, None)
                    case Some(semantic) =>
                      cache.put(text.name, Some(semantic.model))
                      text match {
                        case TextField(name, value, _) => targets.append(EmbedTarget(semantic.model, value))
                        case TextListField(name, values, _) =>
                          values.foreach(value => targets.append(EmbedTarget(semantic.model, value)))
                        case _ => // skip
                      }
                  }
                })
          }

        case _ => // skip
      }
    })
    targets.toList
  }

}

object DocumentEmbedder {
  case class EmbedTarget(model: ModelRef, text: String)

}
