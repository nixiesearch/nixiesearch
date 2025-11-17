package ai.nixiesearch.core

import cats.effect.IO
import org.slf4j.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait Logging {
  protected val logger = LoggerFactory.getLogger(getClass)

  given loggerFactory: org.typelevel.log4cats.LoggerFactory[IO] = Slf4jFactory.create[IO]

  def debug(msg: String): IO[Unit]                = IO(logger.debug(msg))
  def info(msg: String): IO[Unit]                 = IO(logger.info(msg))
  def warn(msg: String): IO[Unit]                 = IO(logger.warn(msg))
  def error(msg: String, ex: Throwable): IO[Unit] = IO(logger.error(msg, ex))
  def error(msg: String): IO[Unit]                = IO(logger.error(msg))
}
