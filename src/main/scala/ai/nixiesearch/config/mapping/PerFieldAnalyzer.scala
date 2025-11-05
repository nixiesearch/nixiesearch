package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.{Field, Logging}
import org.apache.lucene.analysis.{Analyzer, DelegatingAnalyzerWrapper}

case class PerFieldAnalyzer(defaultAnalyzer: Analyzer, mapping: IndexMapping)
    extends DelegatingAnalyzerWrapper(Analyzer.PER_FIELD_REUSE_STRATEGY)
    with Logging {

  override def getWrappedAnalyzer(fieldName: String): Analyzer =
    mapping.fieldSchema(StringName(fieldName)) match {
      case Some(s: TextLikeFieldSchema[?]) =>
        s.search.lexical match {
          case Some(searchParams) => searchParams.analyze.analyzer
          case None               => defaultAnalyzer
        }
      case Some(other) =>
        logger.warn(
          s"Called getWrappedAnalyzer for a non-text field $fieldName (which is $other), this is definitely a bug"
        )
        defaultAnalyzer
      case None => defaultAnalyzer
    }
}
