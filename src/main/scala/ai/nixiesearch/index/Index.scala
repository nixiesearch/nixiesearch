package ai.nixiesearch.index

import java.nio.file.Path
import cats.effect.IO
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory

case class Index(dir: Directory, reader: IndexReader, searcher: IndexSearcher)

object Index {
  def read(dir: Directory) = IO {
    val reader   = DirectoryReader.open(dir)
    val searcher = new IndexSearcher(reader)
    Index(dir, reader, searcher)
  }
  def read(path: Path): IO[Index] = IO {
    val dir      = new MMapDirectory(path)
    val reader   = DirectoryReader.open(dir)
    val searcher = new IndexSearcher(reader)
    Index(dir, reader, searcher)
  }
}
