package ai.nixiesearch.core.nn.onnx

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Paths

class OnnxSessionTest extends AnyFlatSpec with Matchers {
  it should "load and unload dummy model" in {
    val dir = System.getProperty("user.dir") + "/src/test/resources/model/sentence-transformers/all-MiniLM-L6-v2"
    val session = OnnxSession.createUnsafe(
      model = Paths.get(dir, "onnx", "model_opt1_QInt8.onnx"),
      dic = Paths.get(dir, "tokenizer.json")
    )
    session.session.close()
  }
}
