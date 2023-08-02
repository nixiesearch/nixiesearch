package ai.nixiesearch.util

import ai.nixiesearch.api.IndexRoute
import ai.nixiesearch.index.store.Store
import cats.effect.IO
import io.circe.Encoder
import org.http4s.{Entity, EntityDecoder, HttpRoutes, Method, Request, Response, Uri}
import scodec.bits.ByteVector
import io.circe.syntax.*
import cats.effect.unsafe.implicits.global

object HttpTest {
  def sendRaw[T: Encoder](
      route: HttpRoutes[IO],
      url: String,
      payload: Option[T] = None,
      method: Method = Method.PUT
  ): Option[Response[IO]] = {
    val request = Request(
      uri = Uri.unsafeFromString(url),
      entity =
        payload.map(doc => Entity.strict(ByteVector(doc.asJson.noSpaces.getBytes()))).getOrElse(Entity.empty[IO]),
      method = method
    )
    route(request).value.unsafeRunSync()
  }

  def send[T: Encoder, R](
      route: HttpRoutes[IO],
      url: String,
      payload: Option[T] = None,
      method: Method = Method.PUT
  )(implicit dec: EntityDecoder[IO, R]): R = {
    val response = for {
      httpResponseOption <- IO(sendRaw[T](route, url, payload, method))
      httpResponse       <- IO.fromOption(httpResponseOption)(new Exception("got no response"))
      decoded            <- httpResponse.as[R]
    } yield {
      decoded
    }
    response.unsafeRunSync()
  }

}
