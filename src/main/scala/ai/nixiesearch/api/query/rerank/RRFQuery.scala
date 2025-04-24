package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.rerank.RRFQuery.ShardDoc
import ai.nixiesearch.core.Error.BackendError
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.TotalHits.Relation
import org.apache.lucene.search.{ScoreDoc, TopDocs, TotalHits}

import scala.collection.mutable

case class RRFQuery(queries: List[Query]) extends RerankQuery {
  val K = 60.0f

  override def combine(docs: List[TopDocs]): IO[TopDocs] = docs match {
    case head :: Nil => IO.pure(head)
    case Nil         => IO.raiseError(BackendError(s"cannot merge zero query results"))
    case list =>
      IO {
        val docScores = mutable.Map[ShardDoc, Float]()
        for {
          ranking           <- list
          (scoreDoc, index) <- ranking.scoreDocs.zipWithIndex
        } {
          val doc   = ShardDoc(scoreDoc.doc, scoreDoc.shardIndex)
          val score = docScores.getOrElse(doc, 0.0f)
          docScores.put(doc, (score + 1.0f / (K + index)))
        }
        val topDocs = docScores.toList
          .sortBy(-_._2)
          .map { case (doc, score) =>
            new ScoreDoc(doc.docid, score, doc.shardIndex)
          }
          .toArray
        new TopDocs(new TotalHits(topDocs.length, Relation.EQUAL_TO), topDocs)
      }
  }

}

object RRFQuery {
  case class ShardDoc(docid: Int, shardIndex: Int)

  given rrfQueryEncoder: Encoder[RRFQuery] = deriveEncoder
  given rrfQueryDecoder: Decoder[RRFQuery] = deriveDecoder
}
