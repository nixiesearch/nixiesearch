package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.core.nn.model.generative.SSEParserTest.Sample
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.semiauto.*
import fs2.Stream
import cats.effect.unsafe.implicits.global

import scala.util.{Failure, Try}

class SSEParserTest extends AnyFlatSpec with Matchers {
  it should "decode valid streams" in {
    val stream = List(
      """data: {"value": 1}""",
      "",
      """data: [DONE]"""
    )
    val parsed = Stream.emits(stream).through(SSEParser.parse[Sample]).compile.toList.unsafeRunSync()
    parsed shouldBe List(Sample(1))
  }

  it should "fail on bad prefix" in {
    val stream = List(
      """data: {"value": 1}""",
      """xdata: [DONE]"""
    )
    val parsed = Try(Stream.emits(stream).through(SSEParser.parse[Sample]).compile.toList.unsafeRunSync())
    parsed shouldBe a[Failure[?]]
  }
}

object SSEParserTest {
  case class Sample(value: Int)
  given sampleDecoder: Decoder[Sample] = deriveDecoder
}
