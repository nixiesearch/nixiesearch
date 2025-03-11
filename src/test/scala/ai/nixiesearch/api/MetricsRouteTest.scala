package ai.nixiesearch.api

import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.util.TestIndexMapping
import org.http4s.{Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class MetricsRouteTest extends AnyFlatSpec with Matchers {
  it should "return index mapping on GET" in {

    val route = MetricsRoute()
    val response =
      route.routes(Request(uri = Uri.unsafeFromString("http://localhost/metrics"))).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
    val text = response.get.as[String].unsafeRunSync().split('\n')
    text.length should be > 100
  }
}
