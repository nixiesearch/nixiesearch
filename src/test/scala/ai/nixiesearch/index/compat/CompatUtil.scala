package ai.nixiesearch.index.compat

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.{DiskLocation, MemoryLocation}
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.config.{CacheConfig, Config, InferenceConfig}
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.sync.LocalIndex
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.util.{EnvVars, SearchTest}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.apache.lucene.codecs.Codec

import java.io.File
import java.nio.file.Paths

object CompatUtil {
  lazy val pwd  = System.getProperty("user.dir")
  lazy val conf =
    Config.load(new File(s"$pwd/src/test/resources/config/compat.yml"), EnvVars(Map.empty)).unsafeRunSync()
  lazy val mapping                    = conf.schema(IndexName.unsafe("all_types"))
  lazy val inference: InferenceConfig = conf.inference
  lazy val docs                       = List(
    Document(
      List(
        TextField("text_semantic", "test"),
        TextField("text_lexical", "test"),
        TextListField("text_array", List("test")),
        IntField("int", 1),
        LongField("long", 1L),
        FloatField("float", 1.0f),
        DoubleField("double", 1.0),
        DateField("date", 1),
        DateTimeField("datetime", 1L),
        GeopointField("geopoint", 1.0, 2.0)
      )
    )
  )

  def main(args: Array[String]): Unit = {
    val codecs            = Codec.availableCodecs()
    val (clust, shutdown) = writeResource("/tmp/out").allocated.unsafeRunSync()
    shutdown.unsafeRunSync()
  }

  def writeResource(path: String): Resource[IO, Searcher] = for {
    metrics  <- Resource.pure(Metrics())
    models   <- Models.create(inference, CacheConfig(), metrics)
    index    <- LocalIndex.create(mapping, LocalStoreConfig(DiskLocation(Paths.get(path))), models)
    indexer  <- Indexer.open(index, metrics)
    searcher <- Searcher.open(index, metrics)
    _        <- Resource.eval(indexer.addDocuments(docs))
    _        <- Resource.eval(indexer.flush())
    _        <- Resource.eval(indexer.index.sync())
    _        <- Resource.eval(searcher.sync())
  } yield {
    searcher
  }

  def readResource(path: String): Resource[IO, Searcher] = for {
    models   <- Models.create(inference, CacheConfig(), Metrics())
    index    <- LocalIndex.create(mapping, LocalStoreConfig(DiskLocation(Paths.get(path))), models)
    searcher <- Searcher.open(index, Metrics())
  } yield {
    searcher
  }
}
