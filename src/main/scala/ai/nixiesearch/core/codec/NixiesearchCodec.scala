package ai.nixiesearch.core.codec

import ai.nixiesearch.config.mapping.IndexConfig
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.codec.TextFieldWriter
import org.apache.lucene.codecs.lucene912.Lucene912Codec
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat
import org.apache.lucene.codecs.perfield.{PerFieldKnnVectorsFormat, PerFieldPostingsFormat}
import org.apache.lucene.codecs.{Codec, FilterCodec, KnnVectorsFormat, PostingsFormat}
import org.apache.lucene.search.suggest.document.{Completion912PostingsFormat, CompletionPostingsFormat}

class NixiesearchCodec(parent: Codec, config: IndexConfig) extends FilterCodec(parent.getName, parent) with Logging {
  val suggestPostingsFormat = new Completion912PostingsFormat(CompletionPostingsFormat.FSTLoadMode.AUTO)

  override def postingsFormat(): PostingsFormat = new PerFieldPostingsFormat {
    override def getPostingsFormatForField(field: String): PostingsFormat =
      if (field.endsWith(TextFieldWriter.SUGGEST_SUFFIX)) {
        suggestPostingsFormat
      } else {
        delegate.postingsFormat().asInstanceOf[PerFieldPostingsFormat].getPostingsFormatForField(field)
      }
  }

  lazy val knnFormat = new Lucene99HnswVectorsFormat(config.hnsw.m, config.hnsw.efc, config.hnsw.workers, null)
  override def knnVectorsFormat(): KnnVectorsFormat = new PerFieldKnnVectorsFormat() {
    def getKnnVectorsFormatForField(field: String): KnnVectorsFormat = {
      knnFormat
    }
  };

}

object NixiesearchCodec {
  def apply(config: IndexConfig): NixiesearchCodec = {
    new NixiesearchCodec(new Lucene912Codec(), config)
  }
}
