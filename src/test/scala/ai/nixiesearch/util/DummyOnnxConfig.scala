package ai.nixiesearch.util

import ai.nixiesearch.core.nn.onnx.{OnnxConfig, OnnxModelFile}
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device.CPU

case object DummyOnnxConfig extends OnnxConfig {
  override def device: OnnxConfig.Device   = CPU()
  override def file: Option[OnnxModelFile] = None
  override def maxTokens: Int              = 512
}
