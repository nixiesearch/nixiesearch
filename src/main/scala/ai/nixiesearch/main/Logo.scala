package ai.nixiesearch.main

import ai.nixiesearch.util.GPUUtils
import buildinfo.BuildInfo

import java.lang.management.ManagementFactory
import scala.jdk.CollectionConverters.*

object Logo {
  val versionParts = List(
    "version"      -> BuildInfo.version,
    "jdk[build]"   -> BuildInfo.jdk,
    "jdk[runtime]" -> Runtime.version().toString,
    "arch"         -> System.getProperty("os.arch"),
    "build"        -> (if (GPUUtils.isGPUBuild()) "CPU+GPU" else "CPU")
  )
  val args = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.mkString(" ")

  val version = versionParts.map { case (name, value) => s"$name=$value" }.mkString(" ")

  val text =
    s"""███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
       |████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
       |██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
       |██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
       |██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
       |╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
       |$version
       |JVM args: $args                                                                               """.stripMargin
  val lines = text.split("\n").toList
}
