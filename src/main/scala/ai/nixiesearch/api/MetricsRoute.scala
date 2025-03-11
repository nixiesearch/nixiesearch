package ai.nixiesearch.api

import ai.nixiesearch.core.metrics.{Metrics, SystemMetrics}
import cats.effect.IO
import org.http4s.{Headers, HttpRoutes, MediaType, Response, Status}
import org.http4s.dsl.io.*
import fs2.{Chunk, Stream}
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.Entity.Strict
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

import java.io.{ByteArrayOutputStream, OutputStreamWriter}
import java.nio.ByteBuffer

case class MetricsRoute(metrics: Metrics) {
  lazy val format = new PrometheusTextFormatWriter(false)

  val routes = HttpRoutes.of[IO] { case GET -> Root / "metrics" =>
    for {
      _ <- metrics.system.refresh()
      ok <- IO(
        Response[IO](
          status = Status.Ok,
          headers = Headers(`Content-Type`(MediaType.text.plain)),
          entity = Strict(ByteVector(writeMetrics()))
        )
      )

    } yield {
      ok
    }
  }

  def writeMetrics(): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    format.write(stream, metrics.registry.scrape())
    stream.close()
    stream.toByteArray
  }
}
