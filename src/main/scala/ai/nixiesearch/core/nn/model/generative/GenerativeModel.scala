package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.LlamacppParams
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.llamacppserver.LlamacppServer
import ai.nixiesearch.llamacppserver.LlamacppServer.LLAMACPP_BACKEND
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream
import org.http4s.Entity.Strict
import org.http4s.{Entity, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import io.circe.syntax.*
import java.net.{ServerSocket, Socket}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import java.nio.file.Path
import scala.util.Random

trait GenerativeModel {
  def generate(input: String, maxTokens: Int): Stream[IO, String]
}

object GenerativeModel {
  case class LlamacppGenerativeModel(server: LlamacppServer, client: Client[IO], endpoint: Uri, name: ModelRef)
      extends GenerativeModel
      with Logging {
    override def generate(input: String, maxTokens: Int): Stream[IO, String] = for {
      request <- Stream(
        Request[IO](
          method = Method.POST,
          uri = endpoint / "v1" / "chat" / "completions",
          entity = Entity.utf8String(
            ChatML
              .Request(
                model = name.name,
                stream = true,
                messages = List(ChatML.Message(role = "user", content = input)),
                max_tokens = Some(maxTokens),
                seed = Some(42)
              )
              .asJson
              .noSpaces
          )
        )
      )
      response <- client.stream(request)
      chunk <- response.entity.body
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .through(SSEParser.parse[ChatML.Response])
        .filter(_.`object` == "chat.completion.chunk")
      choice      <- Stream.emits(chunk.choices)
      deltaOption <- Stream.fromOption(choice.delta)
      content     <- Stream.fromOption(deltaOption.content)
    } yield {
      content
    }
    def close(): IO[Unit] = info("Closing Llamacpp model") *> IO(server.close())
  }

  object LlamacppGenerativeModel extends Logging {
    def create(
        path: Path,
        options: LlamacppParams,
        useGpu: Boolean,
        name: ModelRef
    ): Resource[IO, LlamacppGenerativeModel] = for {
      port   <- Resource.eval(findPort())
      server <- Resource.make(IO(createServerUnsafe(path, options, useGpu, name, port)))(s => IO(s.close()))
      uri    <- Resource.eval(IO.fromEither(Uri.fromString(s"http://localhost:$port")))
      client <- EmberClientBuilder
        .default[IO]
        .withTimeout(120.second)
        .build
      _ <- Resource.eval(waitForHealthy(client, uri))

    } yield {
      LlamacppGenerativeModel(server, client, uri, name)
    }

    def waitForHealthy(client: Client[IO], uri: Uri): IO[Unit] = {
      Stream
        .iterate[IO, Int](0)(_ + 1)
        .metered(1.second)
        .take(600)
        .evalTap(i => info(s"waiting for llamacpp init (${i * 1.seconds})"))
        .evalMap(_ =>
          client.statusFromUri(uri / "health").map(_.code).recoverWith { case err =>
            debug(s"network error: ${err.getMessage}") *> IO.pure(500)
          }
        )
        .takeWhile(_ != 200)
        .compile
        .drain
    }

    def findPort(): IO[Int] =
      Stream.repeatEval(IO(10000 + Random.nextInt(45000))).evalFilterNot(isPortInUse).take(1).compile.toList.flatMap {
        case Nil       => IO.raiseError(new Exception("Cannot find a port to bind to"))
        case head :: _ => IO.pure(head)
      }

    def isPortInUse(port: Int): IO[Boolean] = {
      val serverSocketResource =
        Resource.make(IO(new ServerSocket(port)))(socket => IO(socket.close()).handleError(_ => ()))
      val clientSocketResource =
        Resource.make(IO(new Socket("localhost", port)))(socket => IO(socket.close()).handleError(_ => ()))

      serverSocketResource
        .use { _ =>
          clientSocketResource.use(_ => IO.pure(false)) // If both sockets are created successfully, port is not in use
        }
        .handleErrorWith(_ => IO.pure(true)) // If an error occurs, the port is in use
    }

    def createServerUnsafe(
        path: Path,
        options: LlamacppParams,
        useGpu: Boolean,
        name: ModelRef,
        port: Int
    ): LlamacppServer = {
      val args = List(
        List("--port", port.toString),
        List("--model", path.toString),
        List("--alias", name.name),
        List("--threads", options.threads.toString),
        List("--gpu_layers", options.gpu_layers.toString),
        if (options.cont_batching) List("--cont-batching") else List("--no-cont-batching"),
        if (options.flash_attn) List("--flash-attn") else List(),
        List("--seed", options.seed.toString)
      )
      logger.info(s"Starting llamacpp-server with args: ${args.map(_.mkString(" ")).mkString(" ")}")
      LlamacppServer.start(
        args.flatten.toArray,
        if (useGpu) LLAMACPP_BACKEND.GGML_CUDA12 else LLAMACPP_BACKEND.GGML_CPU
      )
    }

  }

}
