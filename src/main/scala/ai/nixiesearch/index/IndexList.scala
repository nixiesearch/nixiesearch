package ai.nixiesearch.index

import ai.nixiesearch.config.Config
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream

object IndexList {
  def fromConfig(config: Config): Resource[IO, List[Index]] = {
    Resource.make(
      Stream
        .emits(config.search.values.toList)
        .evalMap(mapping => Index.openOrCreate(mapping, config.core.cache))
        .compile
        .toList
    )(indices => Stream.emits(indices).evalMap(_.close()).compile.drain)

  }
}
