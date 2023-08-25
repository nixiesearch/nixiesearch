package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

trait SearchTest extends AnyFlatSpec with BeforeAndAfterAll {
  def mapping: IndexMapping
  def index: List[Document]

  private val pendingDeleteDirs = new ArrayBuffer[Path]()

  override def afterAll() = {
    pendingDeleteDirs.foreach(path => FileUtils.deleteDirectory(path.toFile))
    super.afterAll()
  }

  trait Index {
    val store = TestLocalStore(mapping)
    pendingDeleteDirs.addOne(store.dir)
    val writer = {
      val w = store.writer(mapping).unsafeRunSync()
      w.addDocuments(index)
      w.flush().unsafeRunSync()
      w
    }
    val searcher = store.reader(mapping).unsafeRunSync().get

    def search(query: Query = MatchAllQuery(), filters: Filter = Filter(), aggs: Aggs = Aggs()): List[String] = {
      searchRaw(query, filters, aggs).hits
        .flatMap(_.fields.collect { case TextField(_, value) => value })
    }

    def searchRaw(
        query: Query = MatchAllQuery(),
        filters: Filter = Filter(),
        aggs: Aggs = Aggs()
    ): SearchRoute.SearchResponse = {
      searcher
        .search(
          query,
          filters = filters,
          fields = List("id"),
          aggs = aggs
        )
        .unsafeRunSync()

    }
  }

}
