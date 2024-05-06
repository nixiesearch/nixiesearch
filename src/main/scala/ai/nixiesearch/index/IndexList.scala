package ai.nixiesearch.index

import ai.nixiesearch.config.Config
import ai.nixiesearch.index.sync.ReplicatedIndex
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream
import cats.implicits.*

object IndexList {
  def fromConfig(config: Config): Resource[IO, List[ReplicatedIndex]] = {
    config.search.values.toList.map(mapping => ReplicatedIndex.create(mapping, config.core.cache)).sequence
  }
}
