package ai.nixiesearch.core.codec.compat

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.SearchParams.QuantStore.Int1
import ai.nixiesearch.config.mapping.SearchParams.{QuantStore, SemanticParams}
import ai.nixiesearch.config.mapping.{FieldName, IndexConfig, IndexMapping}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.Field.TextField
import org.apache.lucene.backward_codecs.lucene912.Lucene912Codec
import org.apache.lucene.backward_codecs.lucene101.Lucene101Codec
import org.apache.lucene.codecs.lucene102.Lucene102HnswBinaryQuantizedVectorsFormat
import org.apache.lucene.codecs.{Codec, FilterCodec, KnnVectorsFormat, PostingsFormat}
import org.apache.lucene.codecs.lucene99.{Lucene99HnswScalarQuantizedVectorsFormat, Lucene99HnswVectorsFormat}
import org.apache.lucene.codecs.perfield.{PerFieldKnnVectorsFormat, PerFieldPostingsFormat}
import org.apache.lucene.search.suggest.document.{
  Completion101PostingsFormat,
  Completion912PostingsFormat,
  CompletionPostingsFormat
}

import scala.collection.mutable

class Nixiesearch101Codec(parent: Codec, mapping: IndexMapping)
    extends NixiesearchCodec("Nixiesearch101", parent, mapping) {

  def this() = {
    this(new Lucene101Codec(), null)
    logger.warn("empty codec constructor called, this should not happen!")
  }

}

object Nixiesearch101Codec {
  def apply(mapping: IndexMapping): Nixiesearch101Codec = {
    new Nixiesearch101Codec(new Lucene101Codec(), mapping)
  }
}
