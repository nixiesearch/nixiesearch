package ai.nixiesearch.util

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success}

case class Size(bytes: Long, literal: String) {
  def kb: Long = math.round(bytes / 1024.0)
  def mb: Long = math.round(bytes / (1024.0 * 1024.0))
}

object Size {
  def mb(value: Long)              = Size(value * 1024 * 1024L, s"$value mb")
  def kb(value: Long)              = Size(value * 1024L, s"$value mb")
  val sizePattern                  = "([0-9\\.]+) ?(k|kb|m|mb|g|gb)?".r
  given sizeEncoder: Encoder[Size] = Encoder.encodeString.contramap(_.literal)
  given sizeDecoder: Decoder[Size] = Decoder.decodeString
    .emapTry(size =>
      size.toLowerCase() match {
        case sizePattern(numstr, unit) =>
          numstr.toDoubleOption match {
            case None => Failure(UserError(s"cannot parse size '$size': $numstr is not a number"))
            case Some(number) =>
              Option(unit) match {
                case Some("kb")  => Success(Size(math.round(number * 1024L), size))
                case Some("mb")  => Success(Size(math.round(number * 1024L * 1024L), size))
                case Some("gb")  => Success(Size(math.round(number * 1024L * 1024L * 1024L), size))
                case Some(other) => Failure(UserError(s"cannot parse size '$size': unexpected unit '$other'"))
                case None        => Success(Size(math.round(number), size))
              }
          }
        case _ => Failure(UserError(s"cannot parse size '$size'"))
      }
    )
}
