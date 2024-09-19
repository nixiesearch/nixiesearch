package ai.nixiesearch.core.codec

import ai.nixiesearch.config.mapping.IndexConfig
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.codec.TextFieldWriter
import org.apache.lucene.codecs.lucene99.{Lucene99Codec, Lucene99HnswVectorsFormat}
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat
import org.apache.lucene.codecs.{Codec, FilterCodec, KnnVectorsFormat, PostingsFormat}
import org.apache.lucene.search.suggest.document.{Completion99PostingsFormat, CompletionPostingsFormat}

class NixiesearchCodec(parent: Codec, config: IndexConfig) extends FilterCodec(parent.getName, parent) with Logging {
  val suggestPostingsFormat = new Completion99PostingsFormat(CompletionPostingsFormat.FSTLoadMode.AUTO)
  val knnFormat = new Lucene99HnswVectorsFormat(
    config.hnsw.m,
    config.hnsw.efc,
    config.hnsw.workers,
    null
  )

  override def postingsFormat(): PostingsFormat = new PerFieldPostingsFormat {
    override def getPostingsFormatForField(field: String): PostingsFormat =
      if (field.endsWith(TextFieldWriter.SUGGEST_SUFFIX)) {
        suggestPostingsFormat
      } else {
        delegate.postingsFormat().asInstanceOf[PerFieldPostingsFormat].getPostingsFormatForField(field)
      }
  }

  override def knnVectorsFormat(): KnnVectorsFormat = knnFormat

}

object NixiesearchCodec {
  def apply(config: IndexConfig): NixiesearchCodec = {
    new NixiesearchCodec(new Lucene99Codec(), config)
  }
}
