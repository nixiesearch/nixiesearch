package ai.nixiesearch.source

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

enum SourceOffset {
  case Latest                                     extends SourceOffset
  case Earliest                                   extends SourceOffset
  case ExactTimestamp(ts: Long)                   extends SourceOffset
  case RelativeDuration(duration: FiniteDuration) extends SourceOffset

}

object SourceOffset {
  val tsPattern       = "ts=([0-9]+)".r
  val durationPattern = "last=([0-9]+)([smhd])".r

  def fromString(str: String): Either[Throwable, SourceOffset] = str match {
    case "earliest"                   => Right(Earliest)
    case "latest"                     => Right(Latest)
    case tsPattern(ts)                => Right(ExactTimestamp(ts.toLong))
    case durationPattern(num, suffix) => Right(RelativeDuration(FiniteDuration(num.toLong, suffix)))
    case other                        => Left(new IllegalArgumentException(s"cannot parse source offset '$other'"))
  }
}
