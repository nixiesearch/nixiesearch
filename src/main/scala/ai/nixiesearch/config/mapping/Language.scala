package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Logging

import scala.util.{Failure, Success}
import io.circe.{Decoder, Encoder}
import io.circe.Json
import org.apache.lucene.analysis.ar.ArabicAnalyzer
import org.apache.lucene.analysis.bg.BulgarianAnalyzer
import org.apache.lucene.analysis.bn.BengaliAnalyzer
import org.apache.lucene.analysis.br.BrazilianAnalyzer
import org.apache.lucene.analysis.ca.CatalanAnalyzer
import org.apache.lucene.analysis.ckb.SoraniAnalyzer
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.cz.CzechAnalyzer
import org.apache.lucene.analysis.da.DanishAnalyzer
import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.analysis.el.GreekAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.es.SpanishAnalyzer
import org.apache.lucene.analysis.et.EstonianAnalyzer
import org.apache.lucene.analysis.eu.BasqueAnalyzer
import org.apache.lucene.analysis.fa.PersianAnalyzer
import org.apache.lucene.analysis.fi.FinnishAnalyzer
import org.apache.lucene.analysis.fr.FrenchAnalyzer
import org.apache.lucene.analysis.ga.IrishAnalyzer
import org.apache.lucene.analysis.gl.GalicianAnalyzer
import org.apache.lucene.analysis.hi.HindiAnalyzer
import org.apache.lucene.analysis.hu.HungarianAnalyzer
import org.apache.lucene.analysis.hy.ArmenianAnalyzer
import org.apache.lucene.analysis.id.IndonesianAnalyzer
import org.apache.lucene.analysis.it.ItalianAnalyzer
import org.apache.lucene.analysis.ja.JapaneseAnalyzer
import org.apache.lucene.analysis.ko.KoreanAnalyzer
import org.apache.lucene.analysis.lt.LithuanianAnalyzer
import org.apache.lucene.analysis.lv.LatvianAnalyzer
import org.apache.lucene.analysis.nl.DutchAnalyzer
import org.apache.lucene.analysis.no.NorwegianAnalyzer
import org.apache.lucene.analysis.pl.PolishAnalyzer
import org.apache.lucene.analysis.pt.PortugueseAnalyzer
import org.apache.lucene.analysis.ro.RomanianAnalyzer
import org.apache.lucene.analysis.ru.RussianAnalyzer
import org.apache.lucene.analysis.sr.SerbianAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.sv.SwedishAnalyzer
import org.apache.lucene.analysis.ta.TamilAnalyzer
import org.apache.lucene.analysis.th.ThaiAnalyzer
import org.apache.lucene.analysis.tr.TurkishAnalyzer
import org.apache.lucene.analysis.uk.UkrainianMorfologikAnalyzer

enum Language(val code: String, val name: String, makeAnalyzer: => Analyzer) extends Logging {
  lazy val analyzer = {
    logger.debug(s"created analyzer for language '$code'")
    makeAnalyzer
  }
  case Generic            extends Language("default", "default", new StandardAnalyzer())
  case English            extends Language("en", "english", new EnglishAnalyzer())
  case Arabic             extends Language("ar", "arabic", new ArabicAnalyzer())
  case Bulgarian          extends Language("bg", "bulgarian", new BulgarianAnalyzer())
  case Bengali            extends Language("bn", "bengali", new BengaliAnalyzer())
  case BrazilianPortugese extends Language("br", "brazilian", new BrazilianAnalyzer())
  case Catalan            extends Language("ca", "catalan", new CatalanAnalyzer())
  case Chinese            extends Language("zh", "chinese", new SmartChineseAnalyzer())
  case SoraniKurdish      extends Language("ckb", "sorani", new SoraniAnalyzer())
  case Czech              extends Language("cz", "czech", new CzechAnalyzer())
  case Danish             extends Language("da", "danish", new DanishAnalyzer())
  case German             extends Language("de", "german", new GermanAnalyzer())
  case Greek              extends Language("el", "greek", new GreekAnalyzer())
  case Spanish            extends Language("es", "spanish", new SpanishAnalyzer())
  case Estonian           extends Language("et", "estonian", new EstonianAnalyzer())
  case Basque             extends Language("eu", "basque", new BasqueAnalyzer())
  case Persian            extends Language("fa", "persian", new PersianAnalyzer())
  case Finnish            extends Language("fi", "finnish", new FinnishAnalyzer())
  case French             extends Language("fr", "french", new FrenchAnalyzer())
  case Irish              extends Language("ga", "irish", new IrishAnalyzer())
  case Galician           extends Language("gl", "galician", new GalicianAnalyzer())
  case Hindi              extends Language("hi", "hindi", new HindiAnalyzer())
  case Hungarian          extends Language("hu", "hungarian", new HungarianAnalyzer())
  case Armenian           extends Language("hy", "armenian", new ArmenianAnalyzer())
  case Indonesian         extends Language("id", "indonesian", new IndonesianAnalyzer())
  case Italian            extends Language("it", "italian", new ItalianAnalyzer())
  case Lithuanian         extends Language("lt", "lithuanian", new LithuanianAnalyzer())
  case Latvian            extends Language("lv", "latvian", new LatvianAnalyzer())
  case Dutch              extends Language("nl", "dutch", new DutchAnalyzer())
  case Norwegian          extends Language("no", "norwegian", new NorwegianAnalyzer())
  case Portuguese         extends Language("pt", "portuguese", new PortugueseAnalyzer())
  case Romanian           extends Language("ro", "romanian", new RomanianAnalyzer())
  case Russian            extends Language("ru", "russian", new RussianAnalyzer())
  case Serbian            extends Language("sr", "serbian", new SerbianAnalyzer())
  case Swedish            extends Language("sv", "swedish", new SwedishAnalyzer())
  case Thai               extends Language("th", "thai", new ThaiAnalyzer())
  case Turkish            extends Language("tr", "turkish", new TurkishAnalyzer())
  case Japanese           extends Language("jp", "japanese", new JapaneseAnalyzer())
  case Polish             extends Language("pl", "polish", new PolishAnalyzer())
  case Korean             extends Language("kr", "korean", new KoreanAnalyzer())
  case Tamil              extends Language("ta", "tamil", new TamilAnalyzer())
  case Ukrainian          extends Language("ua", "ukrainian", new UkrainianMorfologikAnalyzer())
}

object Language extends Logging {
  val languages = Language.values.flatMap(lang => List(lang.code -> lang, lang.name -> lang)).toMap
  def forCode(code: String): Option[Language] = languages.get(code)

  given languageTypeDecoder: Decoder[Language] = Decoder.decodeString.emapTry(code =>
    languages.get(code) match {
      case Some(lang) => Success(lang)
      case None       => Failure(new Exception(s"language '$code' is not supported. maybe try 'english'?"))
    }
  )

  given languageTypeEncoder: Encoder[Language] = Encoder.instance(lang => Json.fromString(lang.code))

}
