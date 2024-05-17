package ai.nixiesearch.core.suggest

import org.apache.lucene.search.suggest.document.TopSuggestDocs

case class GeneratedSuggestions(field: String, prefix: TopSuggestDocs, fuzzy1: TopSuggestDocs, fuzzy2: TopSuggestDocs)
