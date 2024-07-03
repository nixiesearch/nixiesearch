package ai.nixiesearch.index

import ai.nixiesearch.index.IndexStats.{LeafStats, SegmentStats}
import cats.effect.IO
import org.apache.lucene.index.{FieldInfo, IndexReader, LeafReaderContext, SegmentCommitInfo, SegmentInfo, SegmentInfos}
import org.apache.lucene.store.Directory
import io.circe.generic.semiauto.*
import io.circe.Codec
import scala.jdk.CollectionConverters.*

case class IndexStats(luceneVersion: String, segments: List[SegmentStats], leaves: List[LeafStats])

object IndexStats {
  case class SegmentStats(name: String, maxDoc: Int, codec: String, files: List[String], delCount: Int)
  object SegmentStats {
    def apply(commitInfo: SegmentCommitInfo) = {
      new SegmentStats(
        name = commitInfo.info.name,
        maxDoc = commitInfo.info.maxDoc(),
        codec = commitInfo.info.getCodec.getName,
        files = commitInfo.files().asScala.toList,
        delCount = commitInfo.getDelCount
      )
    }
  }

  case class LeafStats(docBase: Int, ord: Int, numDocs: Int, numDeletedDocs: Int, fields: List[FieldStats])
  object LeafStats {
    def apply(ctx: LeafReaderContext): LeafStats = {
      val reader = ctx.reader()
      new LeafStats(
        docBase = ctx.docBase,
        ord = ctx.ord,
        numDocs = reader.numDocs(),
        numDeletedDocs = reader.numDeletedDocs(),
        fields = reader.getFieldInfos.iterator().asScala.toList.map(fi => FieldStats(fi))
      )
    }
  }

  case class FieldStats(
      name: String,
      number: Int,
      storePayloads: Boolean,
      indexOptions: String,
      attributes: Map[String, String],
      vectorDimension: Int,
      vectorEncoding: String,
      vectorSimilarityFunction: String
  )
  object FieldStats {
    def apply(info: FieldInfo) = new FieldStats(
      name = info.name,
      number = info.number,
      storePayloads = info.hasPayloads,
      indexOptions = info.getIndexOptions.name(),
      attributes = info.attributes().asScala.toMap,
      vectorDimension = info.getVectorDimension,
      vectorEncoding = info.getVectorEncoding.name(),
      vectorSimilarityFunction = info.getVectorSimilarityFunction.name()
    )
  }

  given fieldStatsCodec: Codec[FieldStats]     = deriveCodec
  given leafStatsCodec: Codec[LeafStats]       = deriveCodec
  given segmentStatsCodec: Codec[SegmentStats] = deriveCodec
  given indexStatsCodec: Codec[IndexStats]     = deriveCodec

  def fromIndex(dir: Directory, reader: IndexReader): IO[IndexStats] = IO {
    val segmentInfos = SegmentInfos.readLatestCommit(dir)
    IndexStats(
      luceneVersion = segmentInfos.getCommitLuceneVersion.toString,
      segments = segmentInfos.asList().asScala.toList.map(sci => SegmentStats(sci)),
      leaves = reader.leaves().asScala.toList.map(ctx => LeafStats(ctx))
    )
  }
}
