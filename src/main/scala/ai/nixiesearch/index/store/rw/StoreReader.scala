package ai.nixiesearch.index.store.rw

import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.index.store.LocalStore.DirectoryMapping
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  IndexSearcher,
  MatchAllDocsQuery,
  TopDocs,
  Query as LuceneQuery
}
import org.apache.lucene.store.Directory
import org.apache.lucene.document.Document as LuceneDocument
import org.apache.lucene.search.BooleanClause.Occur

case class StoreReader(
    mapping: IndexMapping,
    reader: IndexReader,
    dir: Directory,
    searcher: IndexSearcher,
    analyzer: Analyzer
) extends Logging {
  def search(query: LuceneQuery, fields: List[String], n: Int): IO[List[Document]] = for {
    top  <- IO(searcher.search(query, n))
    docs <- collect(top, fields)
  } yield {
    docs
  }

  def search(query: Query, fields: List[String] = Nil, n: Int = 10, filters: Filter = Filter()): IO[List[Document]] =
    for {
      compiled     <- query.compile(mapping)
      maybeFilters <- filters.compile(mapping)
      merged <- maybeFilters match {
        case Some(filterQuery) =>
          IO {
            compiled match {
              case _: MatchAllDocsQuery => filterQuery
              case other =>
                val merged = new BooleanQuery.Builder()
                merged.add(new BooleanClause(compiled, Occur.MUST))
                merged.add(new BooleanClause(filterQuery, Occur.FILTER))
                merged.build()
            }
          }
        case None => IO.pure(compiled)
      }
      docs <- search(merged, fields, n)
    } yield {
      docs
    }

  def close(): IO[Unit] = info(s"closing index reader for index '${mapping.name}'") *> IO(reader.close())

  private def collect(top: TopDocs, fields: List[String]): IO[List[Document]] = IO {
    val fieldSet = fields.toSet
    val docs = top.scoreDocs.map(doc => {
      val visitor = DocumentVisitor(mapping, fieldSet)
      reader.storedFields().document(doc.doc, visitor)
      visitor.asDocument()
    })
    docs.toList
  }
}

object StoreReader {
  def create(dm: DirectoryMapping): IO[StoreReader] = IO {
    val reader = DirectoryReader.open(dm.dir)
    StoreReader(dm.mapping, reader, dm.dir, new IndexSearcher(reader), dm.analyzer)
  }
}
