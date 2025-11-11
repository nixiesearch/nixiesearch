package ai.nixiesearch.core

import ai.nixiesearch.util.PrintLogger
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

object Logging {
  private val logger = new PrintLogger("ai.nixiesearch.core.Logging")

  def setLogLevel(level: String): Unit = PrintLogger.setLogLevel(level)
  def setLogLevel(level: PrintLogger.LogLevel): Unit = PrintLogger.setLogLevel(level)
  def getLogLevel(): PrintLogger.LogLevel = PrintLogger.getLogLevel()

  def isDebugEnabled: Boolean = logger.isDebugEnabled()
  def isInfoEnabled: Boolean = logger.isInfoEnabled()
  def isWarnEnabled: Boolean = logger.isWarnEnabled()
  def isErrorEnabled: Boolean = logger.isErrorEnabled()

  def debug(msg: String): Unit = logger.debug(msg)
  def info(msg: String): Unit = logger.info(msg)
  def warn(msg: String): Unit = logger.warn(msg)
  def error(msg: String): Unit = logger.error(msg)
  def error(msg: String, ex: Throwable): Unit = logger.error(msg, ex)
}
