package ai.nixiesearch.main.args

import org.rogach.scallop.{ArgType, ValueConverter}

import scala.util.{Failure, Success, Try}

trait ArgConverter[T] extends ValueConverter[T] {
  def convert(value: String): Either[String, T]

  override def parse(s: List[(String, List[String])]): Either[String, Option[T]] = s match {
    case (_, i :: Nil) :: Nil =>
      Try(convert(i)).recover({ case e: Exception => Left(e.toString) }) match {
        case Failure(exception)    => Left(exception.toString)
        case Success(Right(value)) => Right(Some(value))
        case Success(Left(err))    => Left(err)
      }

    case Nil => Right(None)
    case _   => Left("you should provide exactly one argument for this option")
  }
  override val argType: ArgType.V = ArgType.SINGLE
}
