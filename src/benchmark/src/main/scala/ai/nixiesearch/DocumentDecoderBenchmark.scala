package ai.nixiesearch

import ai.nixiesearch.DocumentDecoderBenchmark.Task
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.SemanticSimpleParams
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.{Document, DocumentDecoder}
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.parser.parse
import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Measurement,
  Mode,
  OutputTimeUnit,
  Param,
  Scope,
  Setup,
  State,
  Warmup
}
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

  var INPUT: Task = uninitialized

  @Param(Array("id_only", "movies", "embed"))
  var INPUT_TYPE: String = uninitialized

  @Param(Array("v3"))
  var DECODER: String = uninitialized

  val idOnlySchema = IndexMapping(name = IndexName("test"), fields = Map(StringName("_id") -> IdFieldSchema()))
  val moviesSchema = IndexMapping(
    name = IndexName("test"),
    fields = Map(
      StringName("_id")   -> IdFieldSchema(),
      StringName("title") -> TextFieldSchema(StringName("title")),
      StringName("desc")  -> TextFieldSchema(StringName("desc")),
      StringName("year")  -> IntFieldSchema(StringName("year"))
    )
  )
  val embedSchema = IndexMapping(
    name = IndexName("test"),
    fields = Map(
      StringName("_id")   -> IdFieldSchema(),
      StringName("title") -> TextFieldSchema(
        name = StringName("title"),
        search = SearchParams(semantic = Some(SemanticSimpleParams(dim = 1024)))
      )
    )
  )

  @Setup
  def setup() = {
    INPUT = INPUT_TYPE match {
      case "embed" =>
        val emb = Array.fill(1024)(Random.nextFloat())
        Task(
          json = s"""{"_id": "1", "title": {"text": "aaa", "embedding": [${emb.mkString(",")}]}}""",
          decoder = DECODER match {
            case "v3"  => (a: String) => readFromString[Document](a)(using DocumentDecoder.codec(embedSchema))
            case other => throw NotImplementedError(other)
          }
        )
      case "movies" =>
        Task(
          json = """{"_id": "1", "title": "The Matrix", "desc": "foo bar", "year": 1999}""",
          decoder = DECODER match {
            case "v3"  => (a: String) => readFromString[Document](a)(using DocumentDecoder.codec(moviesSchema))
            case other => throw NotImplementedError(other)
          }
        )
      case "id_only" =>
        Task(
          json = """{"_id": "1"}""",
          decoder = DECODER match {
            case other => throw NotImplementedError(other)
          }
        )
    }
  }

  @Benchmark
  def measureDecoder() = {
    INPUT.decoder(INPUT.json)
  }

  @Benchmark()
  def ast() = {
    parse(INPUT.json)
  }
}

object DocumentDecoderBenchmark {
  case class Task(json: String, decoder: String => Document)
}
