package ai.nixiesearch.util

import ai.nixiesearch.api.filter.Predicate.LatLon
import ai.nixiesearch.config.FieldSchema.{GeopointFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Document
import cats.effect.IO
import fs2.io.readInputStream
import io.circe.Codec
import io.circe.parser.decode

import java.util.zip.GZIPInputStream
import cats.effect.unsafe.implicits.global

object CitiesDataset {
  val BERLIN = LatLon(52.52, 13.4049)
  
  val mapping = IndexMapping(
    name = IndexName("cities"),
    fields = List(
      TextFieldSchema(StringName("_id"), filter = true),
      TextFieldSchema(StringName("city"), facet = true),
      TextFieldSchema(StringName("country"), facet = true),
      GeopointFieldSchema(StringName("location"), filter = true, sort = true)
    ),
    store = LocalStoreConfig(MemoryLocation())
  )

  def apply() = {
    given documentCodec: Codec[Document] = Document.codecFor(mapping)
    readInputStream(IO(new GZIPInputStream(getClass.getResourceAsStream("/datasets/cities/cities.json.gz"))), 1024)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .evalMap(line => IO.fromEither(decode[Document](line)))
      .compile
      .toList
      .unsafeRunSync()
  }
}
