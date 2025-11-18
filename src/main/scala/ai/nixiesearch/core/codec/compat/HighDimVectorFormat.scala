package ai.nixiesearch.core.codec.compat

import org.apache.lucene.codecs.{KnnVectorsFormat, KnnVectorsReader, KnnVectorsWriter}
import org.apache.lucene.index.{SegmentReadState, SegmentWriteState}

class HighDimVectorFormat(nested: KnnVectorsFormat, maxDims: Int = 8192) extends KnnVectorsFormat(nested.getName) {
  override def fieldsReader(state: SegmentReadState): KnnVectorsReader  = nested.fieldsReader(state)
  override def fieldsWriter(state: SegmentWriteState): KnnVectorsWriter = nested.fieldsWriter(state)
  override def getMaxDimensions(fieldName: String): Int                 = maxDims
}
