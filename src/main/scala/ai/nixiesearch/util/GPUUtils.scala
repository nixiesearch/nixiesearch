package ai.nixiesearch.util

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.onnxruntime.OrtEnvironment
import buildinfo.BuildInfo
import cats.effect.IO

import java.nio.file.Paths
import fs2.{Collector, Stream}
import fs2.io.file.{Files, Path}
import fs2.io.readInputStream

import scala.jdk.CollectionConverters.*
import java.io.FileInputStream

object GPUUtils extends Logging {
  case class GPUDevice(id: Int, model: String)

  def isGPUBuild(): Boolean = BuildInfo.gpu

  val CUDART_NAME = "libcudart.so.12"

  def hasCUDA(libraryPath: String = System.getProperty("java.library.path")): IO[Boolean] =
    Stream
      .emits(libraryPath.split(':'))
      .evalFilter(path => Files[IO].exists(Path(path)))
      .evalFilter(path => Files[IO].isDirectory(Path(path)))
      .flatMap(libraryPath => Files[IO].list(Path(libraryPath)).filter(p => p.fileName.toString == CUDART_NAME))
      .evalTap(cudaLib => info(s"$CUDART_NAME found on $cudaLib"))
      .compile
      .toList
      .map(_.nonEmpty)

  def listDevices(driverDir: String = "/proc/driver/nvidia"): IO[List[GPUDevice]] = {
    val driverPath = Path(driverDir)
    Files[IO].exists(driverPath).flatMap {
      case false => IO.pure(Nil)
      case true  =>
        Files[IO].isDirectory(driverPath).flatMap {
          case false => IO.raiseError(BackendError(s"$driverPath exists but not a directory"))
          case true  =>
            Files[IO]
              .list(driverPath / "gpus")
              .evalMap(gpuDir =>
                readInputStream[IO](IO(new FileInputStream((gpuDir / "information").toNioPath.toFile)), 1024).compile
                  .to(Array)
                  .flatMap(bytes => parseInformation(bytes))
              )
              .compile
              .toList
              .map(_.sortBy(_.id))
        }
    }
  }

  val paramsPattern                                       = "([a-zA-Z 0-9]+):\\s+(.*)".r
  def parseInformation(bytes: Array[Byte]): IO[GPUDevice] = for {
    string <- IO(new String(bytes))
    params <- Stream
      .emits(string.split('\n'))
      .evalMap {
        case paramsPattern(name, value) => IO.pure(name -> value)
        case _                          => IO.raiseError(BackendError(s"Cannot parse GPU info:\n${string}"))
      }
      .compile
      .to(Map)
    name     <- IO.fromOption(params.get("Model"))(BackendError(s"GPU info parameter 'Model' not found in $params"))
    idString <- IO.fromOption(params.get("Device Minor"))(
      BackendError(s"GPU info parameter 'Device Minor' not found in $params")
    )
    id <- IO.fromOption(idString.toIntOption)(BackendError(s"cannot parse device minor: $idString"))
  } yield {
    GPUDevice(id, name)
  }
}
