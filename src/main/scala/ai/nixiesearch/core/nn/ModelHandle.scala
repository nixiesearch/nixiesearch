package ai.nixiesearch.core.nn

import io.circe.{Decoder, Encoder, Json}

import scala.util.{Failure, Success}

sealed trait ModelHandle {
  def name: String
  def asList: List[String]
  def modelFile: Option[String]
}

object ModelHandle {
  def apply(ns: String, name: String) = HuggingFaceHandle(ns, name, None)

  case class HuggingFaceHandle(ns: String, name: String, modelFile: Option[String] = None) extends ModelHandle {
    override def asList: List[String] = List(ns, name)

    override def toString: String = modelFile match {
      case None    => s"hf://$ns/$name"
      case Some(f) => s"hf://$ns/$name?modelFile=$f"
    }
  }
  case class LocalModelHandle(dir: String, modelFile: Option[String] = None) extends ModelHandle {
    override def toString: String = modelFile match {
      case None    => s"file://$dir"
      case Some(f) => s"file://$dir?modelFile=$f"
    }
    override def name: String         = dir
    override def asList: List[String] = List(dir)
  }

  val huggingFacePattern = "(hf://)?([a-zA-Z0-9\\-]+)/([0-9A-Za-z\\-_]+)(\\?modelFile=([0-9a-zA-Z\\-\\.]+))?".r
  val localPattern       = "file://?(/[^\\?]*)(\\?modelFile=([0-9a-zA-Z\\-\\.]+))?".r

  given modelHandleDecoder: Decoder[ModelHandle] = Decoder.decodeString.emapTry {
    case huggingFacePattern(_, ns, name, _, mf) => Success(HuggingFaceHandle(ns, name, Option(mf)))
    case localPattern(path, _, mf)              => Success(LocalModelHandle(path, Option(mf)))
    case other                                  => Failure(InternalError(s"cannot parse model handle '$other'"))
  }

  given modelHandleEncoder: Encoder[ModelHandle] = Encoder.instance {
    case HuggingFaceHandle(ns, name, None)    => Json.fromString(s"$ns/$name")
    case HuggingFaceHandle(ns, name, Some(f)) => Json.fromString(s"$ns/$name?modelFile=$f")
    case LocalModelHandle(path, None)         => Json.fromString(s"file://$path")
    case LocalModelHandle(path, Some(f))      => Json.fromString(s"file://$path?modelFile=$f")
  }
}
