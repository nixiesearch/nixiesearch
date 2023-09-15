package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.search.Searcher
import ai.nixiesearch.index.{IndexRegistry, LocalIndex}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

trait SearchTest extends AnyFlatSpec with BeforeAndAfterAll {
  def mapping: IndexMapping
  def index: List[Document]

  private val pendingDeleteDirs = new ArrayBuffer[String]()

  override def afterAll() = {
    pendingDeleteDirs.foreach(path => FileUtils.deleteDirectory(new File(path)))
    super.afterAll()
  }

  trait Index {
    val dir = Files.createTempDirectory("nixie")
    dir.toFile.deleteOnExit()
    val registry =
      IndexRegistry.create(LocalStoreConfig(LocalStoreUrl(dir.toString)), List(mapping)).allocated.unsafeRunSync()._1

    pendingDeleteDirs.addOne(dir.toString)
    val writer = {
      val w = registry.writer(mapping).unsafeRunSync()
      w.addDocuments(index).unsafeRunSync()
      w.flush().unsafeRunSync()
      w
    }
    val searcher = registry.reader(mapping.name).unsafeRunSync().get

    def search(
        query: Query = MatchAllQuery(),
        filters: Filters = Filters(),
        aggs: Aggs = Aggs(),
        fields: List[String] = List("_id"),
        n: Int = 10
    ): List[String] = {
      Searcher
        .search(SearchRequest(query, filters, n, NonEmptyList.fromListUnsafe(fields), aggs), searcher)
        .unsafeRunSync()
        .hits
        .flatMap(_.fields.collect { case TextField(_, value) => value })
    }

    def searchRaw(
        query: Query = MatchAllQuery(),
        filters: Filters = Filters(),
        aggs: Aggs = Aggs(),
        fields: List[String] = List("_id"),
        n: Int = 10
    ): SearchRoute.SearchResponse = {
      Searcher
        .search(SearchRequest(query, filters, n, NonEmptyList.fromListUnsafe(fields), aggs), searcher)
        .unsafeRunSync()

    }
  }

}
