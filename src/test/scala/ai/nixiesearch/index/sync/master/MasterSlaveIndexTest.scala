package ai.nixiesearch.index.sync.master
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.RemoteDiskLocation
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.index.sync.{MasterIndex, SlaveIndex}
import ai.nixiesearch.util.{TestIndexMapping, TestInferenceConfig}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

import java.nio.file.Files

class MasterSlaveIndexTest extends AnyFlatSpec with Matchers {
  def config(): StoreConfig.DistributedStoreConfig = DistributedStoreConfig(
    searcher = MemoryLocation(),
    indexer = MemoryLocation(),
    remote = RemoteDiskLocation(Files.createTempDirectory("nixie_tmp_"))
  )

  it should "start from empty" ignore {
    val (models, modelsShutdown) =
      Models.create(TestInferenceConfig(), CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val (emptyIndex, indexClose) =
      MasterIndex.create(TestIndexMapping(), config(), models).allocated.unsafeRunSync()
    emptyIndex.sync().unsafeRunSync()
    val mf = emptyIndex.replica.readManifest().unsafeRunSync()
    mf.isDefined shouldBe true
    indexClose.unsafeRunSync()
    modelsShutdown.unsafeRunSync()
  }

  it should "start, write, stop" in {
    val (models, modelsShutdown) =
      Models.create(TestInferenceConfig(), CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val (masterIndex, masterClose) =
      MasterIndex.create(TestIndexMapping(), config(), models).allocated.unsafeRunSync()
    val (writer, writerClose) = Indexer.open(masterIndex, Metrics()).allocated.unsafeRunSync()
    writer.addDocuments(List(Document(List(TextField("title", "yolo"))))).unsafeRunSync()
    writer.flush().unsafeRunSync()
    masterIndex.sync().unsafeRunSync()
    writerClose.unsafeRunSync()
    masterClose.unsafeRunSync()
    modelsShutdown.unsafeRunSync()
  }

  it should "start, write, search, stop" in {
    val (models, modelsShutdown) =
      Models.create(TestInferenceConfig(), CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val conf = config()
    val (masterIndex, masterClose) =
      MasterIndex.create(TestIndexMapping(), conf, models).allocated.unsafeRunSync()
    val (writer, writerClose) = Indexer.open(masterIndex, Metrics()).allocated.unsafeRunSync()
    writer.addDocuments(List(Document(List(TextField("title", "yolo"))))).unsafeRunSync()
    writer.flush().unsafeRunSync()
    masterIndex.sync().unsafeRunSync()
    val (slaveIndex, slaveClose) =
      SlaveIndex.create(TestIndexMapping(), conf, models).allocated.unsafeRunSync()
    val (searcher, searcherClose) = Searcher.open(slaveIndex, Metrics()).allocated.unsafeRunSync()
    val response                  = searcher.search(SearchRequest(query = MatchAllQuery())).unsafeRunSync()
    response.hits.size shouldBe 1
    searcherClose.unsafeRunSync()
    slaveClose.unsafeRunSync()
    writerClose.unsafeRunSync()
    masterClose.unsafeRunSync()
    modelsShutdown.unsafeRunSync()

  }

  it should "start, write, stop, search" in {
    val (models, modelsShutdown) =
      Models.create(TestInferenceConfig(), CacheConfig(), Metrics()).allocated.unsafeRunSync()
    val conf = config()
    val (masterIndex, masterClose) =
      MasterIndex.create(TestIndexMapping(), conf, models).allocated.unsafeRunSync()
    val (writer, writerClose) = Indexer.open(masterIndex, Metrics()).allocated.unsafeRunSync()
    writer.addDocuments(List(Document(List(TextField("title", "yolo"))))).unsafeRunSync()
    writer.flush().unsafeRunSync()
    masterIndex.sync().unsafeRunSync()
    writerClose.unsafeRunSync()
    masterClose.unsafeRunSync()

    val (slaveIndex, slaveClose) =
      SlaveIndex.create(TestIndexMapping(), conf, models).allocated.unsafeRunSync()
    val (searcher, searcherClose) = Searcher.open(slaveIndex, Metrics()).allocated.unsafeRunSync()
    val response                  = searcher.search(SearchRequest(query = MatchAllQuery())).unsafeRunSync()
    response.hits.size shouldBe 1
    searcherClose.unsafeRunSync()
    slaveClose.unsafeRunSync()
    modelsShutdown.unsafeRunSync()
  }
}
