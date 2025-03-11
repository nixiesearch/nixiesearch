package ai.nixiesearch.core.metrics

import ai.nixiesearch.core.metrics.SystemMetrics.{DiskUsage, LoadAvg}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SystemMetricsTest extends AnyFlatSpec with Matchers {

  it should "fetch load avg" in {
    val la = LoadAvg.create().unsafeRunSync()
    la.get.la1 should be > 0.0
  }

  it should "get disk usage" in {
    val diskUsages = DiskUsage.create().unsafeRunSync()
    diskUsages.size should be > 1
  }

  it should "refresh system metrics" in {
    val m = SystemMetrics(Metrics().registry)
    m.refresh().unsafeRunSync()
  }
}
