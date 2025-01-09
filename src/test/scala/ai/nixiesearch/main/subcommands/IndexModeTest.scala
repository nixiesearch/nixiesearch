package ai.nixiesearch.main.subcommands

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.URL.LocalURL
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSourceArgs.FileIndexSourceArgs
import ai.nixiesearch.source.FileSource
import ai.nixiesearch.util.TestInferenceConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import java.nio.file.Paths

class IndexModeTest extends AnyFlatSpec with Matchers {
  val mapping = IndexMapping(
    name = IndexName.unsafe("movies"),
    fields = List(
      TextFieldSchema(name = StringName("_id"), filter = true),
      TextFieldSchema(name = StringName("title"), search = LexicalSearch()),
      TextFieldSchema(name = StringName("overview"), search = LexicalSearch())
    ),
    store = LocalStoreConfig(MemoryLocation())
  )
  it should "index docstream from a file" in {
    IndexMode
      .runOffline(
        indexes = List(mapping),
        source = FileSource(
          FileIndexSourceArgs(
            url = LocalURL(Paths.get("src/test/resources/datasets/movies/movies.jsonl.gz")),
            index = "movies"
          )
        ),
        cacheConfig = CacheConfig(),
        index = "movies",
        inference = TestInferenceConfig.empty()
      )
      .unsafeRunSync()
  }

}
