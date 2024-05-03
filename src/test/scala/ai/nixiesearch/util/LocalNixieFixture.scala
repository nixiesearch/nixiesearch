package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global

trait LocalNixieFixture extends AnyFlatSpec {

  def withCluster(index: IndexMapping)(code: LocalNixie => Any): Unit = {
    val cluster = LocalNixie.create(index).unsafeRunSync()
    try {
      code(cluster)
    } finally cluster.close()
  }
}
