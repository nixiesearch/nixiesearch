package ai.nixiesearch.util

import ai.nixiesearch.util.StreamMarkTest.Item
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.{Pipe, Stream}
import cats.effect.unsafe.implicits.global

class StreamMarkTest extends AnyFlatSpec with Matchers {
  it should "mark last" in {
    val result = Stream
      .emits(List(false, false, false, false))
      .through(StreamMark.pipe[Boolean](tail = _ => true))
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe List(false, false, false, true)
  }

  it should "mark first" in {
    val result = Stream
      .emits(List(false, false, false, false))
      .through(StreamMark.pipe[Boolean](first = _ => true))
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe List(true, false, false, false)
  }

}

object StreamMarkTest {
  case class Item(last: Boolean)
}
