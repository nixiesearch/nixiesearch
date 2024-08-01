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

enum Language(val code: String, makeAnalyzer: => Analyzer) extends Logging {
  lazy val analyzer = {
    logger.debug(s"created analyzer for language '$code'")
    makeAnalyzer
  }
  case Generic            extends Language("default", new StandardAnalyzer())
  case English            extends Language("en", new EnglishAnalyzer())
  case Arabic             extends Language("ar", new ArabicAnalyzer())
  case Bulgarian          extends Language("bg", new BulgarianAnalyzer())
  case Bengali            extends Language("bn", new BengaliAnalyzer())
  case BrazilianPortugese extends Language("br", new BrazilianAnalyzer())
  case Catalan            extends Language("ca", new CatalanAnalyzer())
  case Chinese            extends Language("zh", new SmartChineseAnalyzer())
  case SoraniKurdish      extends Language("ckb", new SoraniAnalyzer())
  case Czech              extends Language("cz", new CzechAnalyzer())
  case Danish             extends Language("da", new DanishAnalyzer())
  case German             extends Language("de", new GermanAnalyzer())
  case Greek              extends Language("el", new GreekAnalyzer())
  case Spanish            extends Language("es", new SpanishAnalyzer())
  case Estonian           extends Language("et", new EstonianAnalyzer())
  case Basque             extends Language("eu", new BasqueAnalyzer())
  case Persian            extends Language("fa", new PersianAnalyzer())
  case Finnish            extends Language("fi", new FinnishAnalyzer())
  case French             extends Language("fr", new FrenchAnalyzer())
  case Irish              extends Language("ga", new IrishAnalyzer())
  case Galician           extends Language("gl", new GalicianAnalyzer())
  case Hindi              extends Language("hi", new HindiAnalyzer())
  case Hungarian          extends Language("hu", new HungarianAnalyzer())
  case Armenian           extends Language("hy", new ArmenianAnalyzer())
  case Indonesian         extends Language("id", new IndonesianAnalyzer())
  case Italian            extends Language("it", new ItalianAnalyzer())
  case Lithuanian         extends Language("lt", new LithuanianAnalyzer())
  case Latvian            extends Language("lv", new LatvianAnalyzer())
  case Dutch              extends Language("nl", new DutchAnalyzer())
  case Norwegian          extends Language("no", new NorwegianAnalyzer())
  case Portuguese         extends Language("pt", new PortugueseAnalyzer())
  case Romanian           extends Language("ro", new RomanianAnalyzer())
  case Russian            extends Language("ru", new RussianAnalyzer())
  case Serbian            extends Language("sr", new SerbianAnalyzer())
  case Swedish            extends Language("sv", new SwedishAnalyzer())
  case Thai               extends Language("th", new ThaiAnalyzer())
  case Turkish            extends Language("tr", new TurkishAnalyzer())
  case Japanese           extends Language("jp", new JapaneseAnalyzer())
  case Polish             extends Language("pl", new PolishAnalyzer())
  case Korean             extends Language("kr", new KoreanAnalyzer())
  case Tamil              extends Language("ta", new TamilAnalyzer())
  case Ukrainian          extends Language("ua", new UkrainianMorfologikAnalyzer())
}

object Language extends Logging {
  val languages                               = Language.values.map(lang => lang.code -> lang).toMap
  def forCode(code: String): Option[Language] = languages.get(code)

  given languageTypeDecoder: Decoder[Language] = Decoder.decodeString.emapTry(code =>
    languages.get(code) match {
      case Some(lang) => Success(lang)
      case None       => Failure(new Exception(s"language '$code' is not supported. maybe try 'english'?"))
    }
  )

  given languageTypeEncoder: Encoder[Language] = Encoder.instance(lang => Json.fromString(lang.code))

}
