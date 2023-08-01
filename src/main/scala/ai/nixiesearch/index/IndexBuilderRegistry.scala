package ai.nixiesearch.index

import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.{IO, Ref}
import cats.implicits.*

import java.io.File
import java.nio.file.{Path, Paths}

case class IndexBuilderRegistry(indices: Ref[IO, Map[String, IndexBuilder]]) {
  def get(indexName: String): IO[Option[IndexBuilder]] = indices.get.map(_.get(indexName))
}

object IndexBuilderRegistry {
  def ofOne(builder: IndexBuilder): IO[IndexBuilderRegistry] = for {
    ref <- Ref.of[IO, Map[String, IndexBuilder]](Map(builder.schema.name -> builder))
  } yield {
    IndexBuilderRegistry(ref)
  }

  def create(config: Config): IO[IndexBuilderRegistry] = ???

}
