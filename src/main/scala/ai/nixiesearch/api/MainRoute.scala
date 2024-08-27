package ai.nixiesearch.api

import ai.nixiesearch.api.MainRoute.MainResponse
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.sync.Index
import cats.effect.IO
import io.circe.{Encoder, Printer}
import org.http4s.{Entity, EntityEncoder, Headers, HttpRoutes, MediaType, Response}
import org.http4s.dsl.io.*
import io.circe.generic.semiauto.*
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.http4s.circe.*
import org.http4s.headers.`Content-Type`
import scodec.bits.ByteVector

import scala.util.Random

case class MainRoute() extends Route with Logging {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "assets" / fileName => staticAsset(s"/ui/assets/$fileName")
    case GET -> Root                       => staticAsset("/ui/index.html")
    case GET -> Root / "index.html"        => staticAsset("/ui/index.html")
    case GET -> Root / "index.htm"         => staticAsset("/ui/index.html")

  }

  def staticAsset(path: String): IO[Response[IO]] = for {
    bytes <- IO(IOUtils.resourceToByteArray(path))
    _     <- info(s"GET $path")
  } yield {
    val mediaType =
      MediaType.forExtension(FilenameUtils.getExtension(path)).getOrElse(MediaType.application.`octet-stream`)
    Response[IO](
      headers = Headers(`Content-Type`(mediaType)),
      entity = Entity.strict(ByteVector(bytes))
    )
  }

}

object MainRoute {
  case class IndexInfo(name: String, ui_url: String)
  case class MainResponse(
      engine: String,
      indexes: List[IndexInfo] = Nil,
      docs: String,
      dad_joke: String
  )

  object MainResponse {
    val jokes = List(
      "A grizzly kept talking to me. He was unbearable",
      "What do you say to encourage an asteroid?. Go little rockstar.",
      "Recently I started working with horses. It's a stable job.",
      "Where’s a dogs favorite place to eat? At Woofle House",
      "Why don't oysters share their pearls?. They're shellfish!",
      "Me: I'm not saying a word without my lawyer present!. So where's my present?!"
    )
    def apply(indexes: List[String]) =
      new MainResponse(
        engine = "nixiesearch",
        docs = "https://nixiesearch.ai",
        dad_joke = Random.shuffle(jokes).head,
        indexes = indexes.map(i => IndexInfo(name = i, ui_url = s"http://localhost:8080/$i/_ui"))
      )
  }

  given indexInfoEncoder: Encoder[IndexInfo]              = deriveEncoder
  given mainResponseEncoder: Encoder[MainResponse]        = deriveEncoder
  given mainResponseJson: EntityEncoder[IO, MainResponse] = jsonEncoderWithPrinterOf(Printer.spaces2)
}
