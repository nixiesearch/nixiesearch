package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.rerank.RerankQuery.ShardDoc
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.TotalHits.Relation
import org.apache.lucene.search.{ScoreDoc, TopDocs, TotalHits}
import cats.syntax.all.*
import scala.collection.mutable

case class RRFQuery(retrieve: List[Query], k: Float = 60.0f, window: Option[Int] = None) extends RerankQuery {
  override def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SearchRoute.SortPredicate],
      filter: Option[Filters],
      models: Models,
      aggs: Option[Aggs],
      size: Int
  ): IO[Searcher.TopDocsWithFacets] = for {
    queryTopDocs <- retrieve.traverse(_.topDocs(mapping, readers, sort, filter, models, aggs, window.getOrElse(size)))
    facets       <- IO(MergedFacetCollector(queryTopDocs.map(_.facets), aggs))
    merged       <- combine(mapping, readers, models, queryTopDocs.map(_.docs), size)
  } yield {
    TopDocsWithFacets(merged, facets)
  }

  def combine(
      mapping: IndexMapping,
      readers: Readers,
      models: Models,
      docs: List[TopDocs],
      size: Int
  ): IO[TopDocs] = docs match {
    case head :: Nil => IO.pure(head)
    case Nil         => IO.raiseError(BackendError(s"cannot merge zero query results"))
    case list        =>
      IO {
        val docScores = mutable.Map[ShardDoc, Float]()
        for {
          ranking           <- list
          (scoreDoc, index) <- ranking.scoreDocs.zipWithIndex
        } {
          val doc   = ShardDoc(scoreDoc.doc, scoreDoc.shardIndex)
          val score = docScores.getOrElse(doc, 0.0f)
          docScores.put(doc, (score + 1.0f / (k + index)))
        }
        val topDocs = docScores.toList
          .sortBy(-_._2)
          .map { case (doc, score) =>
            new ScoreDoc(doc.docid, score, doc.shardIndex)
          }
          .take(size)
          .toArray
        new TopDocs(new TotalHits(topDocs.length, Relation.EQUAL_TO), topDocs)
      }
  }

}

object RRFQuery {

  given rrfQueryEncoder: Encoder[RRFQuery] = deriveEncoder
  given rrfQueryDecoder: Decoder[RRFQuery] = Decoder.instance(c =>
    for {
      retrieve <- c.downField("retrieve").as[List[Query]]
      k        <- c.downField("k").as[Option[Float]]
      window   <- c.downField("rank_window_size").as[Option[Int]]
    } yield {
      RRFQuery(retrieve = retrieve, k = k.getOrElse(60.0), window = window)
    }
  )
}
