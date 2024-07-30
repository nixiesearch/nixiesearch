package ai.nixiesearch.api

import ai.nixiesearch.api.MainRoute.MainResponse
import ai.nixiesearch.index.sync.Index
import cats.effect.IO
import io.circe.{Encoder, Printer}
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.dsl.io.*
import io.circe.generic.semiauto.*
import org.http4s.circe.*
import org.http4s.server.websocket.WebSocketBuilder

import scala.util.Random

case class MainRoute(indexes: List[Index]) extends Route {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
    Ok(MainResponse(indexes = indexes.map(_.name.value)))
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
