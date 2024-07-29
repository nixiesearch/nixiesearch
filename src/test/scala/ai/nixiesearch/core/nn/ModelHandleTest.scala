package ai.nixiesearch.core.nn

import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.ModelHandleTest.HandleTest
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.generic.semiauto.*

class ModelHandleTest extends AnyFlatSpec with Matchers {
  it should "decode HF handle with no schema" in {
    parse("nixiesearch/foo") shouldBe Right(HuggingFaceHandle("nixiesearch", "foo"))
  }

  it should "decode HF handle with schema" in {
    parse("hf://nixiesearch/foo") shouldBe Right(HuggingFaceHandle("nixiesearch", "foo"))
  }

  it should "decode HF handle with schema and modelFile" in {
    parse("hf://nixiesearch/foo?file=foo.onnx") shouldBe Right(
      HuggingFaceHandle("nixiesearch", "foo", Some("foo.onnx"))
    )
  }

  it should "decode local handle with single slash" in {
    parse("file://tmp/file") shouldBe Right(LocalModelHandle("/tmp/file"))
  }

  it should "decode local handle with single slash and modelFile" in {
    val lhs = parse("file://tmp/file?file=mf.onnx")
    val rhs = Right(LocalModelHandle("/tmp/file", Some("mf.onnx")))
    lhs shouldBe rhs
  }

  it should "decode local handle with double slash" in {
    parse("file:///tmp/file") shouldBe Right(LocalModelHandle("/tmp/file"))
  }

  def parse(handle: String): Either[Throwable, ModelHandle] = {
    decode[HandleTest](s"""{"handle": "$handle"}""").map(_.handle)
  }
}

object ModelHandleTest {
  case class HandleTest(handle: ModelHandle)
  implicit val handleDecoder: Decoder[HandleTest] = deriveDecoder
}
