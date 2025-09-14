package ai.nixiesearch.core.codec.compat

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import org.apache.lucene.backward_codecs.lucene101.Lucene101Codec
import org.apache.lucene.codecs.lucene103.Lucene103Codec
import org.apache.lucene.codecs.{Codec, FilterCodec}

class Nixiesearch103Codec(parent: Codec, mapping: IndexMapping)
    extends NixiesearchCodec("Nixiesearch103", parent, mapping) {

  def this() = {
    this(new Lucene103Codec(), null)
    logger.warn("empty codec constructor called, this should not happen!")
  }

}

object Nixiesearch103Codec {
  def apply(mapping: IndexMapping): Nixiesearch103Codec = {
    new Nixiesearch103Codec(new Lucene103Codec(), mapping)
  }
}
