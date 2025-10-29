package ai.nixiesearch

import ai.nixiesearch.DocumentDecoderBenchmark.Task
import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName}
import ai.nixiesearch.core.Document
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

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class DocumentDecoderBenchmark {

  var INPUT: Task = uninitialized

  @Param(Array("id_only", "movies"))
  var INPUT_TYPE: String = uninitialized

  @Param(Array("v1", "v2"))
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

  @Setup
  def setup() = {
    INPUT = INPUT_TYPE match {
      case "movies" =>
        Task(
          json = """{"_id": "1", "title": "The Matrix", "desc": "foo bar", "year": 1999}""",
          decoder = DECODER match {
            case "v1"  => Document.decoderFor1(moviesSchema)
            case "v2"  => Document.decoderFor3(moviesSchema)
            case other => throw NotImplementedError(other)
          }
        )
      case "id_only" =>
        Task(
          json = """{"_id": "1"}""",
          decoder = DECODER match {
            case "v1"  => Document.decoderFor1(idOnlySchema)
            case "v2"  => Document.decoderFor3(idOnlySchema)
            case other => throw NotImplementedError(other)
          }
        )
    }
  }

  @Benchmark
  def measureDecoder() = {
    decode[Document](INPUT.json)(using INPUT.decoder)
  }

  @Benchmark()
  def ast() = {
    parse(INPUT.json)
  }
}

object DocumentDecoderBenchmark {
  case class Task(json: String, decoder: Decoder[Document])
}
