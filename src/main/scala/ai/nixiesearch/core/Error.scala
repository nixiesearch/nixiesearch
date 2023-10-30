package ai.nixiesearch.core

sealed trait Error extends Exception

object Error {
  case class BackendError(m: String) extends Exception(m) with Error
  case class UserError(m: String)  extends Exception(m) with Error
}
