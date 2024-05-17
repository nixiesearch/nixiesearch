package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import org.apache.lucene.codecs.lucene99.Lucene99Codec
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat
import org.apache.lucene.codecs.{Codec, FilterCodec, PostingsFormat}
import org.apache.lucene.search.suggest.document.{Completion99PostingsFormat, CompletionPostingsFormat}

class NixiesearchCodec(suggestFields: Set[String], parent: Codec) extends FilterCodec(parent.getName, parent) {
  val suggestPostingsFormat = new Completion99PostingsFormat(CompletionPostingsFormat.FSTLoadMode.AUTO)

  override def postingsFormat(): PostingsFormat = new PerFieldPostingsFormat {
    override def getPostingsFormatForField(field: String): PostingsFormat =
      if (suggestFields.contains(field)) {
        suggestPostingsFormat
      } else {
        delegate.postingsFormat().asInstanceOf[PerFieldPostingsFormat].getPostingsFormatForField(field)
      }
  }
}

object NixiesearchCodec {
  def apply(suggestFields: List[String]): NixiesearchCodec = {
    val baseCodec = new Lucene99Codec()
    new NixiesearchCodec(suggestFields.toSet, baseCodec)
  }
}
