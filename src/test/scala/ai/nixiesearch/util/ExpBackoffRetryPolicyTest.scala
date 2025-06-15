package ai.nixiesearch.util

import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.ExpBackoffRetryPolicyTest.FailFirstNRoute
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.IO
import io.circe.Codec
import org.http4s.{EntityEncoder, HttpRoutes, Request, Uri}
import org.http4s.dsl.io.*
import io.circe.generic.semiauto.*
import org.http4s.circe.*
import org.http4s.client.middleware.Retry
import org.http4s.ember.client.EmberClientBuilder
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Port
import org.http4s.Status.InternalServerError
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

import scala.concurrent.duration.*
import scala.util.Random

class ExpBackoffRetryPolicyTest extends AnyFlatSpec with Matchers with Logging {
  it should "handle retries" in {
    val (client, clientShutdown) =
      EmberClientBuilder.default[IO].withTimeout(10.seconds).build.allocated.unsafeRunSync()
    val retryClient        = Retry[IO](ExpBackoffRetryPolicy(100.millis, 2.0, 4000.millis, 10))(client)
    val app                = Router("/" -> FailFirstNRoute(2).routes).orNotFound
    val port               = 10240 + Random.nextInt(20000)
    val (api, apiShutdown) =
      EmberServerBuilder.default[IO].withHttpApp(app).withPort(Port.fromInt(port).get).build.allocated.unsafeRunSync()
    val response =
      retryClient.successful(Request[IO](uri = Uri.unsafeFromString(s"http://localhost:$port/ok"))).unsafeRunSync()
    response shouldBe true
    // apiShutdown.unsafeRunSync()
    clientShutdown.unsafeRunSync()
  }
}

object ExpBackoffRetryPolicyTest {

  case class FailFirstNRoute(first: Int) extends Logging {
    var count = 0

    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
      if (count < first) {
        count += 1
        logger.info(s"request $count: FAIL")
        InternalServerError()
      } else {
        count += 1
        logger.info(s"request $count: OK")
        Ok()
      }
    }
  }
}
