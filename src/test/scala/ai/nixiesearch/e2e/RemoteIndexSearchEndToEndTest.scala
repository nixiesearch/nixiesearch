package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.config.{CacheConfig, Config}
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.util.{DatasetLoader, EnvVars}
import ai.nixiesearch.util.Tags.EndToEnd
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.io.File

class RemoteIndexSearchEndToEndTest extends AnyFlatSpec with Matchers {
  lazy val pwd  = System.getProperty("user.dir")
  lazy val conf =
    Config
      .load(new File(s"$pwd/src/test/resources/datasets/movies/config-dist.yaml"), EnvVars(Map.empty))
      .unsafeRunSync()
  lazy val mapping = conf.schema(IndexName.unsafe("movies"))

  it should "write index to s3" taggedAs (EndToEnd.Index) in {
    val (models, modelsShutdown)   = Models.create(conf.inference, CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val (master, masterShutdown)   = Index.forIndexing(mapping, models).allocated.unsafeRunSync()
    val (indexer, indexerShutdown) = Indexer.open(master, Metrics()).allocated.unsafeRunSync()
    val docs = DatasetLoader.fromFile(s"$pwd/src/test/resources/datasets/movies/movies.jsonl.gz", mapping)
    indexer.addDocuments(docs).unsafeRunSync()
    indexer.flush().unsafeRunSync()
    master.sync().unsafeRunSync()
    indexerShutdown.unsafeRunSync()
    masterShutdown.unsafeRunSync()
    modelsShutdown.unsafeRunSync()
  }

  it should "search from s3" taggedAs (EndToEnd.Index) in {
    val (models, modelsShutdown)     = Models.create(conf.inference, CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val (slave, slaveShutdown)       = Index.forSearch(mapping, models).allocated.unsafeRunSync()
    val (searcher, searcherShutdown) = Searcher.open(slave, Metrics()).allocated.unsafeRunSync()
    val response                     = searcher.search(SearchRequest(query = MatchAllQuery())).unsafeRunSync()
    response.hits.size shouldBe 10
    searcherShutdown.unsafeRunSync()
    slaveShutdown.unsafeRunSync()
    modelsShutdown.unsafeRunSync()
  }
}
