package ai.nixiesearch.core.metrics

import ai.nixiesearch.core.metrics.SystemMetrics.{DiskUsage, LoadAvg}
import cats.effect.IO
import com.sun.management.OperatingSystemMXBean
import io.prometheus.metrics.core.metrics.Gauge

import java.io.FileInputStream
import java.lang.management.ManagementFactory
import java.nio.file.{FileSystems, Files, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Try

case class SystemMetrics() {
  val os: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[OperatingSystemMXBean]
  val cpuPercent = Gauge.builder().name("nixiesearch_os_cpu_percent").help("Percent CPU used by the OS").register()
  val cpuLoad1   = Gauge.builder().name("nixiesearch_os_cpu_load1").help("1-minute system load average").register()
  val cpuLoad5   = Gauge.builder().name("nixiesearch_os_cpu_load5").help("1-minute system load average").register()
  val cpuLoad15  = Gauge.builder().name("nixiesearch_os_cpu_load15").help("1-minute system load average").register()

  val dataAvailableBytes = Gauge
    .builder()
    .name("nixiesearch_fs_data_available_bytes")
    .help("Available space on device")
    .labelNames("device")
    .register()

  val dataFreeBytes = Gauge
    .builder()
    .name("nixiesearch_fs_data_free_bytes")
    .help("Free space on device")
    .labelNames("device")
    .register()

  val dataSizeBytes = Gauge
    .builder()
    .name("nixiesearch_fs_data_size_bytes")
    .help("Size of the device")
    .labelNames("device")
    .register()

  def refresh(): IO[Unit] = for {
    _             <- IO(cpuPercent.set(os.getCpuLoad))
    loadAvgOption <- LoadAvg.create()
    _ <- loadAvgOption match {
      case None => IO.unit
      case Some(loadAvg) =>
        IO {
          cpuLoad1.set(loadAvg.la1)
          cpuLoad5.set(loadAvg.la5)
          cpuLoad15.set(loadAvg.la15)
        }
    }
    diskUsages <- DiskUsage.create()
    _ <- IO {
      diskUsages.foreach(du => {
        dataAvailableBytes.labelValues(du.device).set(du.usable.toDouble)
        dataFreeBytes.labelValues(du.device).set(du.free.toDouble)
        dataSizeBytes.labelValues(du.device).set(du.total.toDouble)
      })
    }
  } yield {}

}

object SystemMetrics {
  case class LoadAvg(la1: Double, la5: Double, la15: Double)

  object LoadAvg {
    def create(): IO[Option[LoadAvg]] = IO {
      val path = Paths.get("/proc/loadavg")
      if (Files.exists(path) && !Files.isDirectory(path)) {
        val content = Files.readString(path)
        val tokens  = content.split(' ')
        if (tokens.length > 3) {
          Some(LoadAvg(tokens(0).toDouble, tokens(1).toDouble, tokens(2).toDouble))
        } else {
          None
        }
      } else {
        None
      }
    }
  }

  case class DiskUsage(device: String, total: Long, usable: Long, free: Long)
  object DiskUsage {
    def create(): IO[List[DiskUsage]] = IO {
      val result = for {
        store <- FileSystems.getDefault.getFileStores.asScala.toList
      } yield {
        Try(
          DiskUsage(
            device = store.name(),
            total = store.getTotalSpace,
            usable = store.getUsableSpace,
            free = store.getUnallocatedSpace
          )
        ).toOption
      }
      result.flatten
    }
  }
}
