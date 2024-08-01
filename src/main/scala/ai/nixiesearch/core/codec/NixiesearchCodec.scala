package ai.nixiesearch.core.codec

import org.apache.lucene.codecs.lucene99.Lucene99Codec
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat
import org.apache.lucene.codecs.{Codec, FilterCodec, PostingsFormat}
import org.apache.lucene.search.suggest.document.{Completion99PostingsFormat, CompletionPostingsFormat}

class NixiesearchCodec(parent: Codec) extends FilterCodec(parent.getName, parent) {
  val suggestPostingsFormat = new Completion99PostingsFormat(CompletionPostingsFormat.FSTLoadMode.AUTO)

  override def postingsFormat(): PostingsFormat = new PerFieldPostingsFormat {
    override def getPostingsFormatForField(field: String): PostingsFormat =
      if (field.endsWith(TextFieldWriter.SUGGEST_SUFFIX)) {
        suggestPostingsFormat
      } else {
        delegate.postingsFormat().asInstanceOf[PerFieldPostingsFormat].getPostingsFormatForField(field)
      }
  }
}

object NixiesearchCodec {
  def apply(): NixiesearchCodec = {
    new NixiesearchCodec(new Lucene99Codec())
  }
}
