package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.IdFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import io.circe.{Decoder, DecodingFailure, Json}
import org.apache.lucene.search.SortField

case class IdField(name: String, value: String) extends Field {}

object IdField extends FieldCodec[IdField, IdFieldSchema, String] {
  override def decodeJson(spec: IdFieldSchema): Decoder[Option[IdField]] =
    Decoder.instance(c =>
      c.downField("_id").as[String] match {
        case Left(err1) =>
          c.downField("_id").as[Int] match {
            case Left(err2) => Left(DecodingFailure(s"cannot decode _id field: $err1, $err2", c.history))
            case Right(int) => Right(Some(IdField("_id", int.toString)))
          }
        case Right(str) => Right(Some(IdField("_id", str)))
      }
    )

  override def encodeJson(field: IdField): Json = Json.obj("_id" -> Json.fromString(field.value))

  override def readLucene(
      name: String,
      spec: IdFieldSchema,
      value: String
  ): Either[FieldCodec.WireDecodingError, IdField] = ???

  override def writeLucene(field: IdField, spec: IdFieldSchema, buffer: DocumentGroup): Unit = ???

  def sort(reverse: Boolean): SortField = {
    val sortField = new SortField("_id", SortField.Type.STRING, reverse)
    sortField
  }

}
