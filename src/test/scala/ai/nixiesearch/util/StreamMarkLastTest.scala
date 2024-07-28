package ai.nixiesearch.util

import ai.nixiesearch.util.StreamMarkLastTest.Item
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import cats.effect.unsafe.implicits.global

class StreamMarkLastTest extends AnyFlatSpec with Matchers {
  it should "mark last" in {
    val result = Stream
      .emits(List(Item(false), Item(false), Item(false)))
      .through(StreamMarkLast.pipe[Item](m => m.copy(last = true)))
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe List(Item(false), Item(false), Item(true))
  }
}

object StreamMarkLastTest {
  case class Item(last: Boolean)
}
