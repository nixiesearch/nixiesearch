package ai.nixiesearch.core.search

import com.carrotsearch.hppc.IntHashSet
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.facet.FacetsCollector.MatchingDocs
import org.apache.lucene.search.DocIdSet
import org.apache.lucene.util.{BitDocIdSet, FixedBitSet}

import scala.jdk.CollectionConverters.*
import java.util

case class MergedFacetCollector(merged: util.List[FacetsCollector.MatchingDocs]) extends FacetsCollector {
  override def getMatchingDocs: util.List[FacetsCollector.MatchingDocs] = merged
}

object MergedFacetCollector {

  def apply(perField: List[FacetsCollector]): MergedFacetCollector = {
    perField match {
      case head :: Nil => MergedFacetCollector(head.getMatchingDocs)
      case other =>
        val segments = other.flatMap(_.getMatchingDocs.asScala).groupBy(_.context).map { case (ctx, leaves) =>
          val all = new FixedBitSet(ctx.reader().numDocs())
          leaves.foreach(next => all.or(next.bits.iterator()))
          val docIdset    = new BitDocIdSet(all, 1L)
          val matchedDocs = all.length()
          new MatchingDocs(ctx, docIdset, matchedDocs, new Array[Float](matchedDocs))
        }
        new MergedFacetCollector(segments.toList.asJava)
    }

  }
}
