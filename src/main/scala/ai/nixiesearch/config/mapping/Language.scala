package ai.nixiesearch.config.mapping

import scala.util.{Failure, Success}
import io.circe.{Decoder, Encoder}
import io.circe.Json
import org.apache.lucene.analysis.{Analyzer, CharArraySet, Tokenizer}
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import java.util.Locale
import scala.collection.mutable.ArrayBuffer

sealed trait Language {
  def analyzer: Analyzer

}

object Language {
  case object Generic extends Language {
    override val analyzer = new StandardAnalyzer()
  }
  case object English extends Language {
    override val analyzer = new EnglishAnalyzer()
  }

  given languageTypeDecoder: Decoder[Language] = Decoder.decodeString.emapTry {
    case "en" | "english" => Success(English)
    case "generic"        => Success(Generic)
    case other            => Failure(new Exception(s"language '$other' is not supported. maybe try 'english'?"))
  }

  given languageTypeEncoder: Encoder[Language] = Encoder.instance {
    case English => Json.fromString("english")
    case Generic => Json.fromString("generic")
  }

}
