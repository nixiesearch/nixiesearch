package ai.nixiesearch.main.subcommands

import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.CliConfig.CliArgs
import ai.nixiesearch.util.{EnvVars, Version}
import cats.effect.IO

trait Mode[T <: CliArgs] extends Logging {
  def run(args: T, env: EnvVars): IO[Unit]

  def printSystemDetails(cachePath: String) = IO.blocking {
    val totalRam = Runtime.getRuntime.totalMemory() / (1024 * 1024)
    val freeRam  = Runtime.getRuntime.freeMemory() / (1024 * 1024)
    val maxRam   = Runtime.getRuntime.maxMemory() / (1024 * 1024)
    logger.info(s"System info: cpu=${Runtime.getRuntime.availableProcessors()} native=${Version.isGraalVMNativeImage}")
    logger.info(s"RAM: total=${totalRam}MB free=${freeRam}MB max=${maxRam}MB")
    val root       = new java.io.File("/")
    val totalRoot  = root.getTotalSpace / (1024 * 1024)
    val freeRoot   = root.getFreeSpace / (1024 * 1024)
    val usableRoot = root.getUsableSpace / (1024 * 1024)
    logger.info(s"Disk /: total=${totalRoot}MB free=${freeRoot}MB usable=${usableRoot}MB")
    val cache       = new java.io.File(cachePath)
    val totalCache  = cache.getTotalSpace / (1024 * 1024)
    val freeCache   = cache.getFreeSpace / (1024 * 1024)
    val usableCache = cache.getUsableSpace / (1024 * 1024)
    logger.info(s"Disk ${cachePath}: total=${totalCache}MB free=${freeCache}MB usable=${usableCache}MB")
  }
}
