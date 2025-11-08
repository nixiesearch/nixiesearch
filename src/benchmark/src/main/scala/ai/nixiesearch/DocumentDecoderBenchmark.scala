package ai.nixiesearch

import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.SemanticSimpleParams
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.{Document, DocumentDecoder}
import org.openjdk.jmh.annotations.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized
import scala.util.Random

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class DocumentDecoderBenchmark {

  @Param(Array("384", "768", "1024"))
  var DIM: String = uninitialized

  val SCHEMA = IndexMapping(
    name = IndexName("test"),
    fields = Map(
      StringName("_id")   -> IdFieldSchema(),
      StringName("title") -> TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticSimpleParams()))
      )
    )
  )

  var json: String                      = uninitialized
  var decoder: JsonValueCodec[Document] = uninitialized

  @Setup
  def setup() = {
    val emb = Array.fill(1024)(Random.nextFloat())
    json = s"""{"_id": "1", "title": {"text": "aaa", "embedding": [${emb.mkString(",")}]}}"""
    decoder = DocumentDecoder.codec(SCHEMA)
  }

  @Benchmark
  def decodeEmbed() = {
    readFromString[Document](json)(using decoder)

  }

}

object DocumentDecoderBenchmark {
  case class Task(json: String, decoder: String => Document)
}
