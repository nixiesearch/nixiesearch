package ai.nixiesearch.core.search

import ai.nixiesearch.api.SuggestRoute.{SuggestResponse, Suggestion, SuggestionForm}
import ai.nixiesearch.config.mapping.SuggestMapping
import ai.nixiesearch.core.codec.SuggestVisitor
import ai.nixiesearch.index.Index
import cats.effect.IO
import org.apache.lucene.codecs.KnnVectorsReader
import org.apache.lucene.codecs.lucene95.Lucene95Codec
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat.FieldsReader
import org.apache.lucene.index.FloatVectorValues
import org.apache.lucene.search.{TopDocs, TopScoreDocCollector, Query as LuceneQuery}
import org.apache.lucene.util.VectorUtil

import java.util
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

object Suggester {
  case class SuggestVector(form: String, score: Float, vector: Array[Float])
  case class LeafVectorValues(docBase: Int, values: FloatVectorValues)

  def suggest(
      index: Index,
      query: LuceneQuery,
      fetchN: Int,
      n: Int,
      threshold: Double
  ): IO[SuggestResponse] = for {
    start        <- IO(System.currentTimeMillis())
    topCollector <- IO.pure(TopScoreDocCollector.create(fetchN, fetchN))
    _            <- index.syncReader()
    searcher     <- index.searcherRef.get
    _            <- IO(searcher.search(query, topCollector))
    docs         <- collectSuggest(index, topCollector.topDocs(), threshold)
  } yield {
    SuggestResponse(docs)
  }

  private def collectSuggest(index: Index, top: TopDocs, threshold: Double): IO[List[Suggestion]] = for {
    reader <- index.readerRef.get
    leafReaders <- IO {
      reader
        .leaves()
        .asScala
        .map(c => LeafVectorValues(c.docBase, c.reader().getFloatVectorValues(SuggestMapping.SUGGEST_FIELD)))
        .toList
        .sortBy(_.docBase)
    }
    docs <- IO {
      val forms = for {
        doc                <- top.scoreDocs
        segmentFloatValues <- leafReaders.find(_.docBase <= doc.doc)
        visitor = SuggestVisitor(SuggestMapping.SUGGEST_FIELD)
        vector <- {
          reader.storedFields().document(doc.doc, visitor)
          segmentFloatValues.values.advance(doc.doc)
          val arr    = segmentFloatValues.values.vectorValue()
          val vector = util.Arrays.copyOf(arr, arr.length)
          visitor.getResult().map(text => SuggestVector(form = text, score = doc.score, vector = vector))
        }
      } yield {
        vector
      }
      var i      = 0
      val result = ArrayBuffer[Suggestion]()
      while (i < forms.length) {
        var j = i + 1
        if (forms(i) != null) {
          val nextForms = ArrayBuffer[SuggestionForm]()
          while (j < forms.length) {
            if (forms(j) != null) {
              val cosineDistance = VectorUtil.cosine(forms(i).vector, forms(j).vector)
              if (cosineDistance >= threshold) {
                nextForms.addOne(SuggestionForm(forms(j).form, forms(j).score))
                forms(j) = null
              }
            }
            j += 1
          }
          result.addOne(Suggestion(forms(i).form, forms(i).score, nextForms.toList))
        }
        i += 1
      }
      result.toList
    }
  } yield {
    docs
  }

}
