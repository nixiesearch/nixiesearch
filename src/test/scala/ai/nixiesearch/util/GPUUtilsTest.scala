package ai.nixiesearch.util

import ai.nixiesearch.util.GPUUtils.GPUDevice
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.{Files, Paths}

class GPUUtilsTest extends AnyFlatSpec with Matchers {
  it should "detect gpus when present" in {
    val gpuList = GPUUtils
      .listDevices(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "gpu_test").toString)
      .unsafeRunSync()
    gpuList shouldBe List(GPUDevice(0, "NVIDIA GeForce RTX 4090"), GPUDevice(1, "NVIDIA GeForce RTX 4090"))
  }

  it should "list nothing when no gpu" in {
    val gpuList = GPUUtils.listDevices().unsafeRunSync()
    gpuList shouldBe Nil
  }

  it should "check for CUDA when missing" in {
    val hasCuda = GPUUtils.hasCUDA().unsafeRunSync()
    hasCuda shouldBe false
  }

  it should "detect cuda" in {
    val testPath = Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "gpu_test", "lib64").toString
    val hasCuda  = GPUUtils.hasCUDA(System.getProperty("java.library.path") + ":" + testPath).unsafeRunSync()
    hasCuda shouldBe true
  }
}
