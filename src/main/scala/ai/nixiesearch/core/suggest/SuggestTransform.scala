package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.Language
import ai.nixiesearch.config.mapping.SuggestMapping.{SUGGEST_FIELD, Transform}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.{TextField, TextListField}
import ai.nixiesearch.core.codec.TextFieldWriter.RAW_SUFFIX
import ai.nixiesearch.core.search.lucene.SuggestionAnalyzer
import cats.effect.IO
import fs2.Pipe
import org.apache.lucene.search.{Collector, IndexSearcher, MatchAllDocsQuery, Query, TermQuery, TotalHitCountCollector}
import fs2.Stream
import org.apache.lucene.index.Term

object SuggestTransform {
  def doc2suggest(tf: Option[Transform], search: IndexSearcher): Pipe[IO, Document, Document] =
    in =>
      tf match {
        case None => in // pass-through
        case Some(transform) =>
          val analyzer = SuggestionAnalyzer(transform.lowercase, transform.removeStopwords, transform.language)
          in.evalMap(doc =>
            IO {
              val fieldValues = doc.fields.collect {
                case TextField(name, value) if transform.fields.contains(name)      => List(value)
                case TextListField(name, values) if transform.fields.contains(name) => values
              }
              val analyzed = fieldValues.flatten
                .map(line => Language.analyze(analyzer, SUGGEST_FIELD, line))
              val grouped = analyzed
                .flatMap(line => transform.group.flatMap(g => line.sliding(g).map(_.mkString(" "))))
                .distinct
              val filtered = grouped
                .filter(candidate => {
                  val query     = new TermQuery(new Term(SUGGEST_FIELD + RAW_SUFFIX, candidate))
                  val collector = new TotalHitCountCollector()
                  search.search(query, collector)
                  collector.getTotalHits == 0
                })
              filtered.map(doc =>
                Document(
                  List(
                    TextField(SUGGEST_FIELD, doc),
                    TextField("_id", doc)
                  )
                )
              )
            }
          ).flatMap(docs => Stream.emits(docs))
      }

}
