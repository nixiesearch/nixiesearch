package ai.nixiesearch.util

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import scala.concurrent.duration.*
import cats.effect.unsafe.implicits.global

class DurationStreamTest extends AnyFlatSpec with Matchers {
  it should "add took times to items" in {
    val result = Stream
      .emits(Seq(10, 20, 30, 40))
      .evalTap(i => IO.sleep(i.millis))
      .through(DurationStream.pipe(System.currentTimeMillis()))
      .compile
      .toList
      .unsafeRunSync()
    result.map(_._2).forall(_ > 10) shouldBe true
  }
}
