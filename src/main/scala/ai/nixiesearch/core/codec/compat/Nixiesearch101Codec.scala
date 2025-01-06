package ai.nixiesearch.core.codec.compat

import ai.nixiesearch.config.mapping.IndexConfig
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.field.TextField
import org.apache.lucene.backward_codecs.lucene912.Lucene912Codec
import org.apache.lucene.codecs.lucene101.Lucene101Codec
import org.apache.lucene.codecs.{Codec, FilterCodec, KnnVectorsFormat, PostingsFormat}
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat
import org.apache.lucene.codecs.perfield.{PerFieldKnnVectorsFormat, PerFieldPostingsFormat}
import org.apache.lucene.search.suggest.document.{
  Completion101PostingsFormat,
  Completion912PostingsFormat,
  CompletionPostingsFormat
}

class Nixiesearch101Codec(parent: Codec, config: IndexConfig)
    extends FilterCodec("Nixiesearch101", parent)
    with Logging {

  def this() = this(new Lucene101Codec(), IndexConfig())
  val suggestPostingsFormat = new Completion101PostingsFormat(CompletionPostingsFormat.FSTLoadMode.AUTO)

  override def postingsFormat(): PostingsFormat = new PerFieldPostingsFormat {
    override def getPostingsFormatForField(field: String): PostingsFormat =
      if (field.endsWith(TextField.SUGGEST_SUFFIX)) {
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

object Nixiesearch101Codec {
  def apply(config: IndexConfig): Nixiesearch101Codec = {
    new Nixiesearch101Codec(new Lucene101Codec(), config)
  }
}
