package ai.nixiesearch.main.subcommands.util

import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.subcommands.util.LambdaRuntimeClient.ApiGatewayV2Request.RequestContext
import ai.nixiesearch.main.subcommands.util.LambdaRuntimeClient.{ApiGatewayV2Request, ApiGatewayV2Response, LambdaError}
import ai.nixiesearch.util.EnvVars
import cats.effect.{IO, Resource}
import fs2.{Chunk, Stream}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.http4s.{Charset, Entity, EntityDecoder, Header, Headers, MediaType, Method, Request, Response, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import org.http4s.circe.*
import io.circe.syntax.*
import org.apache.commons.codec.binary.Base64
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

case class LambdaRuntimeClient(client: Client[IO], endpoint: Uri) extends Logging {
  def waitForNextInvocation(): IO[ApiGatewayV2Request] = {
    client
      .run(
        Request(
          method = Method.GET,
          uri = endpoint / "2018-06-01" / "runtime" / "invocation" / "next",
          headers = Headers(`Content-Type`(MediaType.application.json))
        )
      )
      .use(response =>
        response
          .as[ApiGatewayV2Request]
          .flatTap(request => debug(s"lambda request: ${request}"))
      )
  }

  def postInvocationError(id: String, error: Throwable): IO[Unit] =
    client
      .run(
        Request(
          method = Method.POST,
          uri = endpoint / "2018-06-01" / "runtime" / "invocation" / id / "error",
          entity = Entity.utf8String(
            LambdaError(
              error.getMessage,
              "Runtime.BackendError",
              error.getStackTrace.map(_.toString).toList
            ).asJson.noSpaces
          )
        )
      )
      .use(response =>
        response
          .as[String]
          .flatMap(text => debug(s"lambda error: ${error.getMessage}, response=$text code=${response.status.code}"))
      )
      .void

  def postInvocationResponse(id: String, response: ApiGatewayV2Response): IO[Unit] =
    client
      .run(
        Request(
          method = Method.POST,
          uri = endpoint / "2018-06-01" / "runtime" / "invocation" / id / "response",
          entity = Entity.utf8String(response.asJson.noSpaces)
        )
      )
      .use(response =>
        response.as[String].flatTap(text => debug(s"lambda success: response=$text code=${response.status.code}"))
      )
      .void
}

object LambdaRuntimeClient extends Logging {
  case class ApiGatewayV2Request(
      version: String,
      routeKey: String,
      rawPath: String,
      rawQueryString: Option[String],
      headers: Map[String, String],
      requestContext: RequestContext,
      body: Option[String],
      isBase64Encoded: Option[Boolean]
  ) {
    def toHttp4s(): IO[Request[IO]] = {
      val uriString = rawQueryString match {
        case Some(qs) if qs.nonEmpty => s"${requestContext.http.path}?$qs"
        case _                       => requestContext.http.path
      }

      for {
        uri    <- IO.fromEither(Uri.fromString(uriString))
        method <- IO.fromEither(Method.fromString(requestContext.http.method))
        h          = Headers(headers.map { case (k, v) => Header.Raw(CIString(k), v) }.toList)
        bodyEntity = (body, isBase64Encoded) match {
          case (None, _)                           => Entity.empty[IO]
          case (Some(content), None | Some(false)) => Entity.string(content, Charset.`UTF-8`)
          case (Some(content), Some(true))         => Entity.strict(ByteVector.view(Base64.decodeBase64(content)))

        }
      } yield {
        Request[IO](method = method, uri = uri, headers = h, entity = bodyEntity)
      }
    }
  }
  object ApiGatewayV2Request {
    case class RequestContext(accountId: String, http: HttpContext, requestId: String)
    case class HttpContext(method: String, path: String, protocol: String)
    given httpContextDecoder: Decoder[HttpContext]            = deriveDecoder
    given requestContextDecoder: Decoder[RequestContext]      = deriveDecoder
    given requestDecoder: Decoder[ApiGatewayV2Request]        = deriveDecoder[ApiGatewayV2Request]
    given requestJson: EntityDecoder[IO, ApiGatewayV2Request] = jsonOf
  }

  case class ApiGatewayV2Response(
      statusCode: Int,
      headers: Map[String, String],
      body: String,
      isBase64Encoded: Boolean = false
  )

  object ApiGatewayV2Response {
    given responseEncoder: Encoder[ApiGatewayV2Response] = deriveEncoder[ApiGatewayV2Response]

    def fromResponse(response: Response[IO]): IO[ApiGatewayV2Response] = for {
      bodyBytes <- response.body.compile.to(Array)
      bodyString = new String(bodyBytes, StandardCharsets.UTF_8)
      headers    = response.headers.headers.map(h => h.name.toString -> h.value).toMap
    } yield {
      ApiGatewayV2Response(
        statusCode = response.status.code,
        headers = headers,
        body = bodyString,
        isBase64Encoded = false
      )
    }
  }

  case class LambdaError(errorMessage: String, errorType: String, stackTrace: List[String])
  object LambdaError {
    given errorEncoder: Encoder[LambdaError] = deriveEncoder
  }

  def create(env: EnvVars): Resource[IO, LambdaRuntimeClient] = for {
    apiEndpoint <- Resource.eval(
      IO.fromOption(env.string("AWS_LAMBDA_RUNTIME_API"))(
        BackendError("cannot find AWS_LAMBDA_RUNTIME_API env var. are we running in AWS Lambda?")
      )
    )
    client   <- EmberClientBuilder.default[IO].withTimeout(15.minutes).withIdleConnectionTime(15.minutes).build
    endpoint <- Resource.eval(IO.fromEither(Uri.fromString(s"http://$apiEndpoint")))
    _        <- Resource.eval(info(s"AWS lambda client started, endpoint: $endpoint"))
  } yield {
    LambdaRuntimeClient(client, endpoint)
  }
}
