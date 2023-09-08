package ai.nixiesearch.config.mapping

import scala.util.{Failure, Success}
import io.circe.{Decoder, Encoder}
import io.circe.Json
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.ArrayBuffer

sealed trait Language {
  def analyzer: Analyzer

  def analyze(field: String, query: String): List[String] = {
    val buf    = new ArrayBuffer[String]()
    val stream = analyzer.tokenStream(field, query)
    stream.reset()
    val term = stream.addAttribute(classOf[CharTermAttribute])
    while (stream.incrementToken()) {
      buf.addOne(term.toString)
    }
    stream.close()
    buf.toList
  }
}

object Language {
  case object Generic extends Language {
    override val analyzer = new KeywordAnalyzer()
  }
  case object English extends Language {
    override val analyzer = new EnglishAnalyzer()
  }

  implicit val languageTypeDecoder: Decoder[Language] = Decoder.decodeString.emapTry {
    case "en" | "english" => Success(English)
    case "generic"        => Success(Generic)
    case other            => Failure(new Exception(s"language '$other' is not supported. maybe try 'english'?"))
  }

  implicit val languageTypeEncoder: Encoder[Language] = Encoder.instance {
    case English => Json.fromString("english")
    case Generic => Json.fromString("generic")
  }
}
