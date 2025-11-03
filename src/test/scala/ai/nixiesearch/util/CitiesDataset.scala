package ai.nixiesearch.util

import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.config.FieldSchema.{GeopointFieldSchema, IdFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.{Document, DocumentDecoder, JsonDocumentStream}
import cats.effect.IO
import fs2.io.readInputStream
import io.circe.Codec
import io.circe.parser.decode
import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.util.zip.GZIPInputStream
import cats.effect.unsafe.implicits.global

object CitiesDataset {
  val BERLIN = LatLon(52.52, 13.4049)

  val mapping = IndexMapping(
    name = IndexName("cities"),
    fields = List(
      IdFieldSchema(StringName("_id")),
      TextFieldSchema(StringName("city"), facet = true),
      TextFieldSchema(StringName("country"), facet = true),
      GeopointFieldSchema(StringName("location"), filter = true, sort = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )

  def apply() = {
    readInputStream(IO(new GZIPInputStream(getClass.getResourceAsStream("/datasets/cities/cities.json.gz"))), 1024)
      .through(JsonDocumentStream.parse(mapping))
      .compile
      .toList
      .unsafeRunSync()
  }
}
