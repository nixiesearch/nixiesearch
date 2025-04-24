package ai.nixiesearch.core.search

import ai.nixiesearch.api.aggregation.Aggs
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.facet.FacetsCollector.MatchingDocs
import org.apache.lucene.util.{BitDocIdSet, FixedBitSet}

import scala.jdk.CollectionConverters.*
import java.util

case class MergedFacetCollector(merged: util.List[FacetsCollector.MatchingDocs]) extends FacetsCollector {
  override def getMatchingDocs: util.List[FacetsCollector.MatchingDocs] = merged
}

object MergedFacetCollector {

  def apply(perField: List[FacetsCollector], aggs: Option[Aggs]): MergedFacetCollector = {
    perField match {
      case head :: Nil => MergedFacetCollector(head.getMatchingDocs)
      // no point of computing facets when we're not requesting them
      case nel if aggs.exists(_.aggs.nonEmpty) =>
        val segments = nel.flatMap(_.getMatchingDocs.asScala).groupBy(_.context).map { case (ctx, leaves) =>
          val all = new FixedBitSet(ctx.reader().numDocs())
          leaves.foreach(next => all.or(next.bits.iterator()))
          val docIdset    = new BitDocIdSet(all, 1L)
          val matchedDocs = all.length()
          new MatchingDocs(ctx, docIdset, matchedDocs, new Array[Float](matchedDocs))
        }
        new MergedFacetCollector(segments.toList.asJava)
      case _ => MergedFacetCollector(util.List.of())
    }

  }
}
