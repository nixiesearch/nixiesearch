package ai.nixiesearch.core.search

import ai.nixiesearch.api.SuggestRoute.{SuggestResponse, Suggestion}
import ai.nixiesearch.config.mapping.SuggestMapping
import ai.nixiesearch.core.codec.SuggestVisitor
import ai.nixiesearch.index.Index
import cats.effect.IO
import org.apache.lucene.search.{TopDocs, TopScoreDocCollector, Query as LuceneQuery}

object Suggester {
  def suggest(index: Index, query: LuceneQuery, n: Int): IO[SuggestResponse] = for {
    start        <- IO(System.currentTimeMillis())
    topCollector <- IO.pure(TopScoreDocCollector.create(n, n))
    _            <- index.syncReader()
    searcher     <- index.searcherRef.get
    _            <- IO(searcher.search(query, topCollector))
    docs         <- collectSuggest(index, topCollector.topDocs())
  } yield {
    SuggestResponse(docs)
  }

  private def collectSuggest(index: Index, top: TopDocs): IO[List[Suggestion]] = for {
    reader <- index.readerRef.get
    docs <- IO {
      val docs = top.scoreDocs.flatMap(doc => {
        val visitor = SuggestVisitor(SuggestMapping.SUGGEST_FIELD)
        reader.storedFields().document(doc.doc, visitor)
        visitor.getResult().map(text => Suggestion(text = text, score = doc.score))

      })
      docs.toList
    }
  } yield {
    docs
  }

}
