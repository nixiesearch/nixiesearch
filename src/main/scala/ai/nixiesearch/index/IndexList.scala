package ai.nixiesearch.index

import ai.nixiesearch.config.Config
import cats.effect.IO

object IndexList {
  def fromConfig(config: Config): IO[List[Index]] =
    fs2.Stream
      .emits(config.search.values.toList)
      .evalMap(mapping => Index.create(mapping, config.store, config.core.cache))
      .compile
      .toList
}
