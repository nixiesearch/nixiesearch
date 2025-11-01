package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.LlamacppParams
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.field.{DateFieldCodec, DateTimeFieldCodec}
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.model.generative.GenerativeModel.LlamacppGenerativeModel.{ModelMetadata, Token, TokenizeRequest, TokenizeResponse, tokenCodec}
import ai.nixiesearch.llamacppserver.LlamacppServer
import ai.nixiesearch.llamacppserver.LlamacppServer.LLAMACPP_BACKEND
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream
import io.circe.Codec
import org.http4s.Entity.Strict
import org.http4s.{Entity, EntityDecoder, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import org.http4s.circe.jsonOf

import java.net.{ServerSocket, Socket}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import java.nio.file.Path
import scala.util.Random

trait GenerativeModel {
  def generate(input: String, maxTokens: Int): Stream[IO, String]
  def prompt(instruction: String, docs: List[Document], maxTokensPerDoc: Int, fields: List[FieldName]): IO[String]
}

object GenerativeModel {
  case class LlamacppGenerativeModel(
      server: LlamacppServer,
      client: Client[IO],
      endpoint: Uri,
      name: ModelRef,
      metadata: ModelMetadata
  ) extends GenerativeModel
      with Logging {
    override def generate(input: String, maxTokens: Int): Stream[IO, String] = for {
      tokenized <- Stream.eval(tokenize(input))
      truncated <- Stream(tokenized.take(metadata.n_ctx_train))
      _         <- Stream.eval(
        IO.whenA(truncated.size < tokenized.size)(
          warn(s"Trimmed ${tokenized.size} -> ${metadata.n_ctx_train} due to too long context")
        )
      )
      request <- Stream(
        Request[IO](
          method = Method.POST,
          uri = endpoint / "v1" / "chat" / "completions",
          entity = Entity.utf8String(
            ChatML
              .Request(
                model = name.name,
                stream = true,
                messages = List(ChatML.Message(role = "user", content = truncated.map(_.piece).mkString(""))),
                max_tokens = Some(maxTokens),
                seed = Some(42)
              )
              .asJson
              .noSpaces
          )
        )
      )
      response <- client.stream(request)
      chunk    <- response.entity.body
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

    def tokenize(text: String): IO[List[Token]] = for {
      response <- client.expect[TokenizeResponse](
        Request[IO](
          method = Method.POST,
          uri = endpoint / "tokenize",
          entity = Entity.utf8String(TokenizeRequest(text, with_pieces = true).asJson.noSpaces)
        )
      )
    } yield {
      response.tokens
    }

    override def prompt(
        instruction: String,
        docs: List[Document],
        maxTokensPerDoc: Int,
        fields: List[FieldName]
    ): IO[String] = for {
      instructionTokens <- tokenize(instruction + "\n\n")
      docTokensTrimmed  <- Stream
        .emits[IO, Document](docs)
        .parEvalMap(Runtime.getRuntime.availableProcessors())(doc =>
          IO {
            val stringifiedFields = doc.fields
              .filter(f => fields.isEmpty || fields.exists(_.matches(StringName(f.name))))
              .flatMap {
                case f if f.name == "_id"          => None
                case f if f.name == "_score"       => None
                case IntField(name, value)         => Some(s"$name: $value")
                case DateField(name, value)        => Some(s"$name: ${DateFieldCodec.writeString(value)}")
                case LongField(name, value)        => Some(s"$name: $value")
                case TextField(name, value, _)     => Some(s"$name: $value")
                case FloatField(name, value)       => Some(s"$name: $value")
                case DoubleField(name, value)      => Some(s"$name: $value")
                case BooleanField(name, value)     => Some(s"$name: $value")
                case DateTimeField(name, value)    => Some(s"$name: ${DateTimeFieldCodec.writeString(value)}")
                case TextListField(name, value, _) => Some(s"$name: ${value.mkString(", ")}")
                case GeopointField(_, _, _)        => None
                case _                             => None
              }
            stringifiedFields match {
              case Nil => ""
              case nel => nel.mkString("", "\n", "\n\n")
            }

          }
        )
        .parEvalMap(Runtime.getRuntime.availableProcessors())(str => tokenize(str))
        .map(_.take(maxTokensPerDoc))
        .reduce(_ ++ _)
        .compile
        .lastOrError
        .map(_.take(metadata.n_ctx_train - instructionTokens.size))
      prompt <- IO(instructionTokens ++ docTokensTrimmed)
      _ <- info(s"inst_tokens=${instructionTokens.size} payload_tokens=${prompt.size} max_ctx=${metadata.n_ctx_train}")
    } yield {
      prompt.map(_.piece).mkString("")
    }
  }

  object LlamacppGenerativeModel extends Logging {
    case class Token(id: Int, piece: String)
    case class TokenizeRequest(content: String, with_pieces: Boolean)
    case class TokenizeResponse(tokens: List[Token])
    given tokenCodec: Codec[Token]                                  = deriveCodec
    given tokenizeRequestCodec: Codec[TokenizeRequest]              = deriveCodec
    given tokenizeResponseCodec: Codec[TokenizeResponse]            = deriveCodec
    given tokenizeResponseJson: EntityDecoder[IO, TokenizeResponse] = jsonOf

    case class ModelMetadata(vocab_type: Int, n_vocab: Int, n_ctx_train: Int, n_embd: Int, n_params: Long, size: Long)
    case class ModelResponse(id: String, created: Long, meta: ModelMetadata)
    case class ModelsListResponse(data: List[ModelResponse])
    given modelMetaCodec: Codec[ModelMetadata]                                = deriveCodec
    given modelResponseCodec: Codec[ModelResponse]                            = deriveCodec
    given modelListResponseCodec: Codec[ModelsListResponse]                   = deriveCodec
    given modelListResponseJsonDecoder: EntityDecoder[IO, ModelsListResponse] = jsonOf

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
      _      <- Resource.eval(waitForHealthy(client, uri))
      models <- Resource.eval(client.expect[ModelsListResponse](uri / "v1" / "models"))
      meta   <- Resource.eval(
        IO.fromOption(models.data.headOption)(BackendError("expected model metadata in llamacpp response"))
      )
    } yield {
      LlamacppGenerativeModel(server, client, uri, name, meta.meta)
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
        .flatTap(_ => info("llamacpp server initialized successfully"))
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
