package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import cats.effect.IO
import org.apache.lucene.search
import org.apache.lucene.search.TopFieldDocs

trait RerankQuery extends Logging {
  def queries: List[Query]
  
  def rank() = ???
}
